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
import java.text.SimpleDateFormat;
import java.util.List;

@Component
@Slf4j(topic = "api")
public class ScanInternalTransactionServlet extends RateLimiterServlet{

    @Autowired
    private ChainBaseManager chainBaseManager;

    SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

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
                }
            }


        });
        fileWriter.close();
    }

    private String bytesToHexString(ByteString bs) {
        byte[] bArr = bs.toByteArray();
        StringBuffer sb = new StringBuffer(bArr.length);
        String sTmp;

        for (int i = 0; i < bArr.length; i++) {
            sTmp = Integer.toHexString(0xFF & bArr[i]);
            if (sTmp.length() < 2)
                sb.append(0);
            sb.append(sTmp.toLowerCase());
        }

        return sb.toString();
    }

    private void recordFile(Protocol.TransactionInfo transactionInfo, int internalTransactionIndex, FileWriter fileWriter, Protocol.InternalTransaction internalTransaction) throws IOException {
        long blockTimeStamp = transactionInfo.getBlockTimeStamp();

        String contractAddress = Hex.toHexString(transactionInfo.getContractAddress().toByteArray());


        String txId = bytesToHexString(transactionInfo.getId());
        long blockNumber = transactionInfo.getBlockNumber();

        String time = simpleDateFormat.format(blockTimeStamp);

        String callerAddress = Hex.toHexString(internalTransaction.getCallerAddress().toByteArray());
        String toAddress = Hex.toHexString(internalTransaction.getTransferToAddress().toByteArray());

        fileWriter.write(String.format("%s\t%s\t%d\t%s\t%d\t%s\t%s\n", time, contractAddress, blockNumber, txId, internalTransactionIndex,
                callerAddress, toAddress));
        fileWriter.flush();
    }
}
