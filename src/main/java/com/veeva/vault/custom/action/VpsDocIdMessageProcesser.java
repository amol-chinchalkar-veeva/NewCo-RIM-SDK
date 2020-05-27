/*
 * --------------------------------------------------------------------
 * MessageProcessor:	VpsDocIdMessageProcesser
 * Author:				achinchalkar @ Veeva
 * Date:				2020-05-26
 *---------------------------------------------------------------------
 * Description:
 *---------------------------------------------------------------------
 * Copyright (c) 2020 Veeva Systems Inc.  All Rights Reserved.
 *		This code is based on pre-existing content developed and
 *		owned by Veeva Systems Inc. and may only be used in connection
 *		with the deliverable with which it was provided to Customer.
 *---------------------------------------------------------------------
 */
package com.veeva.vault.custom.action;

import com.veeva.vault.custom.util.VpsVQLHelper;
import com.veeva.vault.custom.util.api.VpsAPIClient;
import com.veeva.vault.sdk.api.core.LogService;
import com.veeva.vault.sdk.api.core.ServiceLocator;
import com.veeva.vault.sdk.api.core.ValueType;
import com.veeva.vault.sdk.api.core.VaultCollections;
import com.veeva.vault.sdk.api.query.QueryResponse;
import com.veeva.vault.sdk.api.queue.*;

import java.math.BigDecimal;
import java.util.Map;

@MessageProcessorInfo()
public class VpsDocIdMessageProcesser implements MessageProcessor {

    private static final String DOCFIELD_EXPORT_FILENAME = "export_filename__v";
    private static final String DOCFIELD_NEWCO_DOCUMENT_ID = "newco_document_id__c"; //newco_document_id__c


    public void execute(MessageContext context) {
        LogService logger = ServiceLocator.locate(LogService.class);
        Message message = context.getMessage();
        // String remoteVaultId = context.getRemoteVaultId();
        String docId = context.getMessage().getAttribute("docId", MessageAttributeValueType.STRING);
        String base30DocumentId = context.getMessage().getAttribute("base30DocumentId", MessageAttributeValueType.STRING);
        String apiConnection = context.getMessage().getAttribute("apiConnection", MessageAttributeValueType.STRING);
        String majorVersionNumber = context.getMessage().getAttribute("majorVersionNumber", MessageAttributeValueType.STRING);
        String minorVersionNumber = context.getMessage().getAttribute("minorVersionNumber", MessageAttributeValueType.STRING);
        VpsAPIClient apiClient = new VpsAPIClient(apiConnection);

        Map<String, String> documentFieldsToUpdate = VaultCollections.newMap();
        documentFieldsToUpdate.put(DOCFIELD_NEWCO_DOCUMENT_ID, base30DocumentId);
        documentFieldsToUpdate.put(DOCFIELD_EXPORT_FILENAME, base30DocumentId);

        if (!base30DocumentId.equals("")) {
            boolean updateSuccess = false;
            updateSuccess = apiClient.updateDocumentFields(docId, majorVersionNumber,
                    minorVersionNumber, documentFieldsToUpdate);
            if (!updateSuccess) {
                logger.error("Failed to update document with id {}", docId + "_" + majorVersionNumber +
                        "_" + minorVersionNumber);
            } else {
                logger.info("Successfully updated document with id {}", docId + "_" + majorVersionNumber +
                        "_" + minorVersionNumber);
            }
        }

    }
}

