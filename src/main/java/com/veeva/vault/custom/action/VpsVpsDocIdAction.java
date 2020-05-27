/*
 * --------------------------------------------------------------------
 * DocumentAction:	VpsVpsDocIdAction
 * Object:			document
 * Author:			achinchalkar @ Veeva
 * Date:			2020-05-26
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

import com.veeva.vault.sdk.api.action.*;
import com.veeva.vault.sdk.api.core.*;
import com.veeva.vault.sdk.api.document.*;
import com.veeva.vault.sdk.api.queue.Message;
import com.veeva.vault.sdk.api.queue.PutMessageResponse;
import com.veeva.vault.sdk.api.queue.QueueService;

import java.util.List;

@DocumentActionInfo(label = "Set Queue Doc Id",
        usages = Usage.UNSPECIFIED)
public class VpsVpsDocIdAction implements DocumentAction {

    private static final String QUEUE_NAME = "doc_id_queue__c";

    public boolean isExecutable(DocumentActionContext context) {
        return true;
    }

    public void execute(DocumentActionContext documentActionContext) {
        DocumentService documentService = ServiceLocator.locate((DocumentService.class));
        LogService logService = ServiceLocator.locate(LogService.class);

        List<DocumentVersion> documentList = VaultCollections.newList();

        for (DocumentVersion documentVersion : documentActionContext.getDocumentVersions()) {
            //Spark Message Queue
            //Create a new Spark Message that is destined for the queue.
            //Set message attributes and items and put message in a queue for delivery
            QueueService queueService = ServiceLocator.locate (QueueService.class);


            // TODO put classification info in here?
            Message message = queueService.newMessage(QUEUE_NAME)
                    .setAttribute ("docid", documentVersion.getValue("id", ValueType.STRING))
                    .setAttribute ("docname", documentVersion.getValue("name__v", ValueType.STRING))
                    .setAttribute ("docmajorversion", documentVersion.getValue("major_version_number__v", ValueType.NUMBER).toString())
                    .setAttribute ("docminorversion", documentVersion.getValue("minor_version_number__v", ValueType.NUMBER).toString());

            //Put the new message into the Spark outbound queue.
            //The PutMessageResponse can be used to review if queuing was successful or not
            PutMessageResponse response = queueService.putMessage(message);

            logService.info("Put 'document' Message in Queue - state = 'docid");

            //Check that the message queue successfully processed the message.
            if (response.getError() != null) {
                logService.info("ERROR Queuing Failed: " + response.getError().getMessage());
            }
            else {
                response.getPutMessageResults().stream().forEach( result -> {
                    logService.info("SUCCESS: " + result.getMessageId() + " " + result.getConnectionName());
                });
            }
        }

        if (documentList.size() > 0) {
            documentService.saveDocumentVersions(documentList);
        }
    }
}