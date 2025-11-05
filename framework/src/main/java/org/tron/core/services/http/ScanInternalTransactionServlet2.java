package org.tron.core.services.http;


import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.encoders.Hex;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@Slf4j(topic = "api")
public class ScanInternalTransactionServlet2 extends InternalTransServlet {

    @Autowired
    private ChainBaseManager chainBaseManager;

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {

        String begin = request.getParameter("begin");
        long beginBlock = Long.parseLong(begin);

        FileWriter fileWriter = new FileWriter("internalTransCreated.txt");
        TransactionRetStore transactionRetStore = chainBaseManager.getTransactionRetStore();
        Set<String> suicideAddresses = new HashSet<>();
        transactionRetStore.getRevokingDB().iterator().forEachRemaining(entry -> {
            byte[] value = entry.getValue();
            TransactionRetCapsule transactionRetCapsule;
            try {
                transactionRetCapsule = new TransactionRetCapsule(value);
            } catch (BadItemException e) {
                throw new RuntimeException(e);
            }
            Protocol.TransactionRet transactionRet = transactionRetCapsule.getInstance();
            long blockNumber = transactionRet.getBlockNumber();
            if (blockNumber < beginBlock) {
                return;
            }
            List<Protocol.TransactionInfo> transactioninfoList = transactionRet.getTransactioninfoList();
            for (Protocol.TransactionInfo transactionInfo : transactioninfoList) {
                int i = 0;
                for (Protocol.InternalTransaction internalTransaction : transactionInfo.getInternalTransactionsList()) {
                    String note = new String(internalTransaction.getNote().toByteArray());
                    if (note.equals("suicide")) {
                        String callerAddress = Hex.toHexString(internalTransaction.getCallerAddress().toByteArray());
                        suicideAddresses.add(callerAddress);
                        continue;
                    }
                    if (note.equals("created")) {
                        String toAddress = Hex.toHexString(internalTransaction.getTransferToAddress().toByteArray());
                        if (suicideAddresses.contains(toAddress)) {
                            try {
                                recordFile(transactionInfo, i, fileWriter, internalTransaction);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                            suicideAddresses.remove(toAddress);
                        }
                    }
                }
            }
        });
        fileWriter.close();
    }
}
