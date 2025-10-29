package org.tron.core.services.http;


import com.google.protobuf.ByteString;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.encoders.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tron.common.utils.ByteArray;
import org.tron.core.ChainBaseManager;
import org.tron.core.capsule.TransactionRetCapsule;
import org.tron.core.db2.common.IRevokingDB;
import org.tron.core.exception.BadItemException;
import org.tron.core.store.TransactionRetStore;
import org.tron.protos.Protocol;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Scanner;

@Component
@Slf4j(topic = "api")
public class FilterInternalTransactionServlet extends InternalTransServlet {

    @Autowired
    private ChainBaseManager chainBaseManager;

    @SneakyThrows
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        FileReader fileReader = new FileReader("transToScan.txt");
        FileWriter fileWriter = new FileWriter("internalTransShifted.txt");
        Scanner sc = new Scanner(fileReader);
        IRevokingDB revokingDB = chainBaseManager.getTransactionRetStore().getRevokingDB();
        while (sc.hasNextLine()) {
            String str = sc.next();
            String[] split = str.split("\t");
            Long blockNumber = Long.parseLong(split[0]);
            logger.info("blockNumber is " + blockNumber);
            String trxs =split[1];
            byte[] value = revokingDB.getUnchecked(ByteArray.fromLong(blockNumber));
            if (Objects.isNull(value)) {
                logger.info("transaction is null");
                continue;
            }
            TransactionRetCapsule transactionRet = new TransactionRetCapsule(value);
            List<Protocol.TransactionInfo> transactioninfoList = transactionRet.getInstance().getTransactioninfoList();
            for (Protocol.TransactionInfo transactionInfo : transactioninfoList) {
                String txId = bytesToHexString(transactionInfo.getId());
                if (!trxs.contains(txId)) {
                    continue;
                }
                HashSet<String> created = new HashSet<>();
                int i = 0;
                for (Protocol.InternalTransaction internalTransaction : transactionInfo.getInternalTransactionsList()) {
                    String note = new String(internalTransaction.getNote().toByteArray());
                    if (note.equals("create")) {
                        String toAddress = Hex.toHexString(internalTransaction.getTransferToAddress().toByteArray());
                        created.add(toAddress);
                        continue;
                    }
                    if (note.equals("suicide")) {
                        String callerAddress = Hex.toHexString(internalTransaction.getCallerAddress().toByteArray());
                        if (created.contains(callerAddress)) {
                            created.remove(callerAddress);
                            continue;
                        }
                        try {
                            recordFile(transactionInfo, i, fileWriter, internalTransaction);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            }
        }
        fileReader.close();
        fileWriter.close();
    }
}
