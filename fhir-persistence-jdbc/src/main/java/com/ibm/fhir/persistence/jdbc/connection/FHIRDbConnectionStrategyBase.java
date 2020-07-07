/*
 * (C) Copyright IBM Corp. 2020
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ibm.fhir.persistence.jdbc.connection;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.sql.DataSource;
import javax.transaction.TransactionSynchronizationRegistry;

import com.ibm.fhir.config.FHIRConfigHelper;
import com.ibm.fhir.config.FHIRConfiguration;
import com.ibm.fhir.config.FHIRRequestContext;
import com.ibm.fhir.config.PropertyGroup;
import com.ibm.fhir.database.utils.model.DbType;
import com.ibm.fhir.persistence.exception.FHIRPersistenceException;
import com.ibm.fhir.persistence.jdbc.exception.FHIRPersistenceDataAccessException;

/**
 * Common base for multi-tenant connection strategy implementations
 */
public abstract class FHIRDbConnectionStrategyBase implements FHIRDbConnectionStrategy {
    private static final Logger log = Logger.getLogger(FHIRDbConnectionStrategyBase.class.getName());
    // We use the sync registry to remember connections we've configured in the current transaction.
    private final TransactionSynchronizationRegistry trxSyncRegistry;

    // the action chain to be applied to new connections
    private final Action newConnectionAction;
    
    // Type and capability 
    private final FHIRDbFlavor flavor;
    
    /**
     * Protected constructor
     * @param trxSyncRegistry the transaction sync registry
     * @param newConnectionAction actions to apply when a connection is created
     */
    protected FHIRDbConnectionStrategyBase(TransactionSynchronizationRegistry trxSyncRegistry, Action newConnectionAction) throws FHIRPersistenceDataAccessException {
        this.trxSyncRegistry = trxSyncRegistry;
        this.newConnectionAction = newConnectionAction;
        
        // initialize the flavor from the configuration
        this.flavor = createFlavor();
    }

    /**
     * Check with the transaction sync registry to see if this is the first time
     * we've worked with this connection in the current transaction.
     * @param connection the new connection
     * @param tenantId the tenant to which the connection belongs
     * @param dsId the datasource in the tenant to which the connection belongs
     */
    protected void configure(Connection connection, String tenantId, String dsId) throws FHIRPersistenceException {
        // We prefix the  key with the name of this class to avoid any potential conflict with other
        // users of the sync registry.        
        final String key = this.getClass().getName() + "/" + tenantId + "/" + dsId;
        if (trxSyncRegistry.getResource(key) == null) {
            if (log.isLoggable(Level.FINE)) {
                log.fine("Configuring new connection in this transaction. Key='" + key + "'");
            }
            
            // first time...so we need to apply actions. Will be cleared when the transaction commits
            newConnectionAction.performOn(connection);
            
            // and register the key so we don't do this again
            trxSyncRegistry.putResource(key, new Object());
        } else {
            if (log.isLoggable(Level.FINE)) {
                log.fine("Connection already configured. Key='" + key + "'");
            }
        }
    }
    
    /**
     * Identify the flavor of the database using information from the
     * datasource configuration.
     * @return
     * @throws FHIRPersistenceException
     */
    private FHIRDbFlavor createFlavor() throws FHIRPersistenceDataAccessException {
        FHIRDbFlavor result;
        
        String datastoreId = FHIRRequestContext.get().getDataStoreId();

        // Retrieve the property group pertaining to the specified datastore.
        // Find and set the tenantKey for the request, otherwise subsequent pulls from the pool
        // miss the tenantKey.
        String dsPropertyName = FHIRConfiguration.PROPERTY_DATASOURCES + "/" + datastoreId;
        PropertyGroup dsPG = FHIRConfigHelper.getPropertyGroup(dsPropertyName);
        if (dsPG != null) {
            
            try {
                boolean multitenant = false;
                String typeValue = dsPG.getStringProperty("type");
                
                DbType type = DbType.from(typeValue);
                if (type == DbType.DB2) {
                    // We make this absolute for now. May change in the future if we
                    // support a single-tenant schema in DB2.
                    multitenant = true;
                }
                
                result = new FHIRDbFlavorImpl(type, multitenant);
            } catch (Exception x) {
                log.log(Level.SEVERE, "No type property found for datastore '" + datastoreId + "'", x);
                throw new FHIRPersistenceDataAccessException("Datastore configuration issue. Details in server logs");
            }
        } else {
            log.log(Level.SEVERE, "Missing datastore configuration for '" + datastoreId + "'");
            throw new FHIRPersistenceDataAccessException("Datastore configuration issue. Details in server logs");
        }
        
        return result;
    }
    
    /**
     * Get a connection configured for the given tenant and datasourceId
     * @param datasource
     * @param tenantId
     * @param dsId
     * @return
     */
    protected Connection getConnection(DataSource datasource, String tenantId, String dsId) throws SQLException, FHIRPersistenceException {
        // Now use the dsId/tenantId specific JEE datasource to get a connection
        Connection connection = datasource.getConnection();
        
        try {
            // always
            connection.setAutoCommit(false);
            
            // configure the connection if it's the first time we've accessed it in this transaction
            configure(connection, tenantId, dsId);
        } catch (Throwable t) {
            // clean up if something goes wrong during configuration
            try {
                connection.close();
            } catch (Throwable x) {
                // NOP...something bad is going on anyway, so don't confuse things
                // by throwing a different exception and hiding the original
            } finally {
                // just to prevent future coding mistakes
                connection = null;
            }
            throw t;
        }
        
        return connection;
    }

    @Override
    public FHIRDbFlavor getFlavor() throws FHIRPersistenceDataAccessException {
        return this.flavor;
    }
}
