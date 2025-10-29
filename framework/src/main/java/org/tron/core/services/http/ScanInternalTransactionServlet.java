package org.tron.core.services.http;

import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tron.core.ChainBaseManager;
import org.tron.core.capsule.TransactionRetCapsule;
import org.tron.core.exception.BadItemException;
import org.tron.core.store.TransactionRetStore;
import org.tron.protos.Protocol;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

@Component
@Slf4j(topic = "api")
public class ScanInternalTransactionServlet extends InternalTransServlet{

    @Autowired
    private ChainBaseManager chainBaseManager;

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        FileWriter fileWriter = new FileWriter("internalTrans.txt");
        TransactionRetStore transactionRetStore = chainBaseManager.getTransactionRetStore();
        transactionRetStore.getRevokingDB().iterator().forEachRemaining(entry -> {
            byte[] value = entry.getValue();
            TransactionRetCapsule transactionRetCapsule;
            try {
                transactionRetCapsule = new TransactionRetCapsule(value);
            } catch (BadItemException e) {
                throw new RuntimeException(e);
            }
            Protocol.TransactionRet transactionRet = transactionRetCapsule.getInstance();
            List<Protocol.TransactionInfo> transactioninfoList = transactionRet.getTransactioninfoList();
            for (Protocol.TransactionInfo transactionInfo : transactioninfoList) {
                int i = 0;
                for (Protocol.InternalTransaction internalTransaction : transactionInfo.getInternalTransactionsList()) {
                    ByteString note = internalTransaction.getNote();
                    String str = new String(note.toByteArray());
                    if (str.equals("suicide")) {
                        try {
                            recordFile(transactionInfo, i, fileWriter, internalTransaction);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    i++;
                }
            }
        });
        fileWriter.close();
    }
}
