package org.tron.core.services.http;

import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.util.encoders.Hex;
import org.tron.protos.Protocol;

import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

@Slf4j(topic = "api")
public class InternalTransServlet extends RateLimiterServlet{

    SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    protected String bytesToHexString(ByteString bs) {
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

    protected void recordFile(Protocol.TransactionInfo transactionInfo, int internalTransactionIndex, FileWriter fileWriter, Protocol.InternalTransaction internalTransaction) throws IOException {
        long blockTimeStamp = transactionInfo.getBlockTimeStamp();

        String contractAddress = Hex.toHexString(transactionInfo.getContractAddress().toByteArray());


        String txId = bytesToHexString(transactionInfo.getId());
        long blockNumber = transactionInfo.getBlockNumber();

        String time = simpleDateFormat.format(blockTimeStamp);

        String callerAddress = Hex.toHexString(internalTransaction.getCallerAddress().toByteArray());
        String toAddress = Hex.toHexString(internalTransaction.getTransferToAddress().toByteArray());

        List<Protocol.InternalTransaction.CallValueInfo> callValueInfoList = internalTransaction.getCallValueInfoList();

        long balance = getBalance(callValueInfoList);
        String trc10Info = getTrc10Info(callValueInfoList);

        fileWriter.write(String.format("%s\t%s\t%d\t%s\t%d\t%s\t%s\t%d\t%s\n", time, contractAddress, blockNumber, txId, internalTransactionIndex,
                callerAddress, toAddress, balance, trc10Info));
        fileWriter.flush();
    }

    protected String getTrc10Info(List<Protocol.InternalTransaction.CallValueInfo> callValueInfoList) {
        List<String> result = new ArrayList<>();
        for (Protocol.InternalTransaction.CallValueInfo callValueInfo : callValueInfoList) {
            if (!StringUtils.isEmpty(callValueInfo.getTokenId())) {
                result.add(callValueInfo.getTokenId() + ":" + callValueInfo.getCallValue());
            }
        }
        if (result.size() == 0) {
            return "";
        }
        return String.join(",", result);
    }

    protected long getBalance(List<Protocol.InternalTransaction.CallValueInfo> callValueInfoList) {
        for (Protocol.InternalTransaction.CallValueInfo callValueInfo : callValueInfoList) {
            if (StringUtils.isEmpty(callValueInfo.getTokenId())) {
                return callValueInfo.getCallValue();
            }
        }
        return 0;
    }

}
