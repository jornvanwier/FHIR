/*
 * (C) Copyright IBM Corp. 2021
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ibm.fhir.schema.control;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

import com.ibm.fhir.database.utils.api.DataAccessException;
import com.ibm.fhir.database.utils.api.IDatabaseAdapter;
import com.ibm.fhir.database.utils.api.IDatabaseStatement;
import com.ibm.fhir.database.utils.api.IDatabaseTranslator;
import com.ibm.fhir.database.utils.api.PartitionInfo;
import com.ibm.fhir.database.utils.db2.Db2CatalogAccess;
import com.ibm.fhir.database.utils.model.DbType;

/**
 * DOMAINRESOURCE_ and RESOURCE_ are abstract tables which are unused.
 * V0021 does the following:
 * 1 - Check the Tables are Empty
 * 2 - Check if not empty or forced
 * 3 - Remove the Tables/Indices and Views
 */
public class MigrateV0021AbstractTypeRemoval implements IDatabaseStatement {

    private static final Logger LOG = Logger.getLogger(MigrateV0021AbstractTypeRemoval.class.getName());

    // The Adapter
    private final IDatabaseAdapter adapter;

    // The FHIR Admin schema
    private final String adminSchemaName;

    // The FHIR data schema
    private final String schemaName;

    // Force
    private final boolean force;

    // Total number of Tables that have data.
    private int count = 0;

    /**
     * Public constructor
     *
     * @param adapter
     * @param adminSchemaName
     * @param schemaName
     * @param force - indicating if their is data in these tables, it'll still remove the table.
     */
    public MigrateV0021AbstractTypeRemoval(IDatabaseAdapter adapter, String adminSchemaName, String schemaName, boolean force) {
        this.adapter = adapter;
        this.adminSchemaName = adminSchemaName;
        this.schemaName = schemaName;
        this.force = force;
    }

    @Override
    public void run(IDatabaseTranslator translator, Connection c) {
        switch (translator.getType()) {
        case POSTGRESQL:
        case DERBY:
            checkDataTables(translator, c);
            checkShouldThrowException();
            removeBaseArtifacts(translator, c);
            break;
        case DB2:
            checkDataInAllPartitionedTenantTables(translator, c);
            checkShouldThrowException();
            removePartitions(translator, c);
            removeBaseArtifacts(translator, c);
            break;
        }
        cleanupHistory(translator, c);
    }

    /**
     * Controls the throwing of Exceptions.
     */
    private void checkShouldThrowException() {
        if (count != 0 && !force) {
            throw new IllegalArgumentException("The Data Tables for Resources or DomainResource contains data. use --force-unused-table-removal to force removal");
        }
    }

    /**
     * cleans up the the VersionHistoryService for a specific schema.
     *
     * This method is idempotent. run... run... run... and it'll cleanup the VHS tables - table and view,
     *
     * @param translator
     * @param c
     */
    private void cleanupHistory(IDatabaseTranslator translator, Connection c) {
        // Clean up the Tables and Views and Index for the DomainResource and Resource Table Group.
        final String sql =
                "DELETE FROM FHIR_ADMIN.VERSION_HISTORY"
                + " WHERE (OBJECT_NAME LIKE 'DOMAINRESOURCE_%'"
                + "   OR OBJECT_NAME LIKE 'RESOURCE_%')"
                + "   AND SCHEMA_NAME = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, schemaName);
            int vhsChanged = ps.executeUpdate();
            LOG.info("VersionHistoryServce: removed =[" + vhsChanged + "]");
        } catch (SQLException x) {
            throw translator.translate(x);
        }
    }

    /**
     * checks the data tables on all partitions
     * @param translator
     * @param c
     */
    private void checkDataInAllPartitionedTenantTables(IDatabaseTranslator translator, Connection c) {
        GetTenantList list = new GetTenantList(adminSchemaName);
        List<TenantInfo> tenants = list.run(translator, c);

        for (TenantInfo tenant : tenants) {
            final String stmtVariable = String.format("SET %s.SV_TENANT_ID = %d", adminSchemaName, tenant.getTenantId());
            try (Statement s = c.createStatement();) {
                s.execute(stmtVariable);
            } catch (SQLException x) {
                throw translator.translate(x);
            }
            checkDataTables(translator, c);
        }
    }

    /**
     * checks the data tables for any content in the deprecated table.
     *
     * @param translator
     * @param c
     */
    private void checkDataTables(IDatabaseTranslator translator, Connection c) {
        for (String deprecatedTable : UnusedTableRemovalNeedsV0021Migration.DEPRECATED_TABLES) {
            final String table = schemaName + "." + deprecatedTable;

            // When checking for data... SYSCAT.TABLES->CARD was considered to be checked.
            // However if the db hasn't collected statistics -1 is returned, and unreliable
            // Instead we're going to check if it has at least one row...
            final String sql = "SELECT * FROM " + table + " " + translator.limit("1");
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                if (ps.execute()) {
                    ResultSet rs = ps.getResultSet();
                    if (rs.next()) {
                        LOG.warning("Data Table contains data '" + table + "'");
                        count++;
                    }
                }
            } catch (SQLException x) {
                throw translator.translate(x);
            }
        }
    }

    /**
     * removes the base artifacts from the database.
     * @param translator
     * @param c
     */
    private void removeBaseArtifacts(IDatabaseTranslator translator, Connection c) {
        List<String> tables = Arrays.asList("DOMAINRESOURCE_", "RESOURCE_");
        List<String> valueTypes = Arrays.asList(
                                        "DATE_VALUES",
                                        "LATLNG_VALUES",
                                        "NUMBER_VALUES",
                                        "QUANTITY_VALUES",
                                        "RESOURCE_TOKEN_REFS",
                                        "STR_VALUES",
                                        "PROFILES",
                                        "TAGS",
                                        "SECURITY");

        // Run across both tables
        for (String tablePrefix : tables) {
            // Drop the Values Tables
            for (String valueType : valueTypes) {
                adapter.dropTable(schemaName, tablePrefix + valueType);
            }

            // Drop the supporting tables
            adapter.dropTable(schemaName, tablePrefix + "logical_resources");
            adapter.dropTable(schemaName, tablePrefix + "resources");

            // Drop the View for the Table
            adapter.dropView(schemaName, tablePrefix + "_token_values_V");
        }

        // Pattern for multitenant is to prefix tables with DRP_
        String prefix = "";
        if (translator.getType() == DbType.DB2) {
            prefix = "DRP_";
        }

        // Drop the tables, when the tables don't exists the errors are swallowed
        // and logs print warnings saying the tables don't exist. That's OK.
        for (String deprecatedTable : UnusedTableRemovalNeedsV0021Migration.DEPRECATED_TABLES) {
            adapter.dropTable(schemaName, prefix + deprecatedTable);
        }
    }

    /**
     * remove partitions
     * @param translator
     * @param c
     */
    private void removePartitions(IDatabaseTranslator translator, Connection c) {
        // Need to drop the partitions first and check all the partitions.
        Db2CatalogAccess catalog = new Db2CatalogAccess(c);
        for (String deprecatedTable : UnusedTableRemovalNeedsV0021Migration.DEPRECATED_TABLES) {
            List<PartitionInfo> pis = catalog.getPartitionList(schemaName, deprecatedTable);
            for (PartitionInfo pi : pis) {
                try {
                    catalog.dropPartition(schemaName, deprecatedTable, pi);
                } catch (DataAccessException ex) {
                    // SQLCODE=-20251, SQLSTATE=428G means it's the last partition https://www.ibm.com/docs/en/db2/11.1?topic=messages-sql20250-sql20499
                    if (ex.getCause() instanceof SQLException) {
                        SQLException sqe = (SQLException) ex.getCause();
                        if (sqe.getErrorCode() == -20251 && "428G2".equals(sqe.getSQLState())) {
                            LOG.info("Partition is last one and can't be dropped");
                            continue;
                        }
                        if (sqe.getErrorCode() == -270 && "42997".equals(sqe.getSQLState())) {
                            LOG.info("Partition is already dropped");
                            continue;
                        }
                    }
                    throw ex;
                }
            }
        }

        // Remove the Base artifacts
        removeBaseArtifacts(translator, c);
    }
}