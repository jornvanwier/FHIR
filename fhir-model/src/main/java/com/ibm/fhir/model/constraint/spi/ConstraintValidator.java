/*
 * (C) Copyright IBM Corp. 2021
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ibm.fhir.model.constraint.spi;

import com.ibm.fhir.model.annotation.Constraint;
import com.ibm.fhir.model.resource.Resource;
import com.ibm.fhir.model.type.Element;
import com.ibm.fhir.model.visitor.Visitable;

/**
 * An interface for programmatically evaluating constraints against a validation target {@link Element} or {@link Resource}
 */
public interface ConstraintValidator<T extends Visitable> {
    /**
     * Indicates whether an element or resource is valid with respect to the given constraint
     *
     * @param elementOrResource
     *     the element or resource
     * @param constraint
     *     the constraint
     * @return
     *     true if the element or resource is valid with respect to the given constraint, false otherwise
     */
    boolean isValid(T elementOrResource, Constraint constraint);
}
