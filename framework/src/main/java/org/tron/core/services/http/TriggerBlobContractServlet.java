package org.tron.core.services.http;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.protobuf.ByteString;
import io.netty.util.internal.StringUtil;
import java.io.IOException;
import java.security.InvalidParameterException;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tron.api.GrpcAPI.Return;
import org.tron.api.GrpcAPI.Return.response_code;
import org.tron.api.GrpcAPI.TransactionExtention;
import org.tron.common.utils.ByteArray;
import org.tron.core.Wallet;
import org.tron.core.exception.ContractValidateException;
import org.tron.protos.Protocol.BlobTxSidecar;
import org.tron.protos.Protocol.Transaction;
import org.tron.protos.Protocol.Transaction.Contract.ContractType;
import org.tron.protos.contract.SmartContractOuterClass.BlobContract;

@Component
@Slf4j(topic = "API")
public class TriggerBlobContractServlet extends RateLimiterServlet {

  @Autowired
  private Wallet wallet;

  protected void doGet(HttpServletRequest request, HttpServletResponse response) {
  }

  private void validateParameter(String contract) {
    JSONObject jsonObject = JSONObject.parseObject(contract);
    if (StringUtil.isNullOrEmpty(jsonObject.getString(Util.OWNER_ADDRESS))) {
      throw new InvalidParameterException(Util.OWNER_ADDRESS + " isn't set.");
    }
    if (StringUtil.isNullOrEmpty(jsonObject.getString(Util.CONTRACT_ADDRESS))) {
      throw new InvalidParameterException(Util.CONTRACT_ADDRESS + " isn't set.");
    }
  }

  protected void doPost(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    BlobContract.Builder build = BlobContract.newBuilder();
    TransactionExtention.Builder trxExtBuilder = TransactionExtention.newBuilder();
    Return.Builder retBuilder = Return.newBuilder();
    boolean visible = false;
    try {
      String contract = request.getReader().lines()
          .collect(Collectors.joining(System.lineSeparator()));
      Util.checkBodySize(contract);
      visible = Util.getVisiblePost(contract);
      validateParameter(contract);
      JsonFormat.merge(contract, build, visible);
      JSONObject jsonObject = JSONObject.parseObject(contract);

      boolean isFunctionSelectorSet =
          !StringUtil.isNullOrEmpty(jsonObject.getString(Util.FUNCTION_SELECTOR));
      if (isFunctionSelectorSet) {
        String selector = jsonObject.getString(Util.FUNCTION_SELECTOR);
        String parameter = jsonObject.getString(Util.FUNCTION_PARAMETER);
        String data = Util.parseMethod(selector, parameter);
        build.setData(ByteString.copyFrom(ByteArray.fromHexString(data)));
      }

      build.setCallTokenValue(Util.getJsonLongValue(jsonObject, "call_token_value"));
      build.setTokenId(Util.getJsonLongValue(jsonObject, "token_id"));
      build.setCallValue(Util.getJsonLongValue(jsonObject, "call_value"));
      build.setBlobFeeCap(Util.getJsonLongValue(jsonObject, "blob_fee_cap"));

      JSONObject sidecarJo = jsonObject.getJSONObject("sidecar");
      BlobTxSidecar.Builder blobTxSidecar = BlobTxSidecar.newBuilder();
      JSONArray blobs = Util.getJsonArray(sidecarJo, "blobs", true);
      for (int i = 0; i < blobs.size(); i++) {
        blobTxSidecar.addBlobs(ByteString.fromHex(blobs.getString(i)));
      }
      JSONArray commitments = Util.getJsonArray(sidecarJo, "commitments", true);
      for (int i = 0; i < commitments.size(); i++) {
        blobTxSidecar.addCommitments(ByteString.fromHex(commitments.getString(i)));
      }
      JSONArray proofs = Util.getJsonArray(sidecarJo, "proofs", true);
      for (int i = 0; i < proofs.size(); i++) {
        blobTxSidecar.addProofs(ByteString.fromHex(proofs.getString(i)));
      }
      build.setSidecar(blobTxSidecar);

      JSONArray blobHashes = Util.getJsonArray(jsonObject, "blob_hashes", true);
      for (int i = 0; i < blobHashes.size(); i++) {
        build.addBlobHashes(ByteString.fromHex(blobHashes.getString(i)));
      }
      long feeLimit = Util.getJsonLongValue(jsonObject, "fee_limit");

      Transaction trx = wallet
          .createTransactionCapsule(build.build(), ContractType.BlobContract).getInstance();
      Transaction.Builder txBuilder = trx.toBuilder();
      Transaction.raw.Builder rawBuilder = trx.getRawData().toBuilder();
      rawBuilder.setFeeLimit(feeLimit);
      txBuilder.setRawData(rawBuilder);

      trx = Util.setTransactionPermissionId(jsonObject, trx);
      trxExtBuilder.setTransaction(trx);
      retBuilder.setResult(true).setCode(response_code.SUCCESS);
    } catch (ContractValidateException e) {
      retBuilder.setResult(false).setCode(response_code.CONTRACT_VALIDATE_ERROR)
          .setMessage(ByteString.copyFromUtf8(e.getMessage()));
    } catch (Exception e) {
      String errString = null;
      if (e.getMessage() != null) {
        errString = e.getMessage().replaceAll("[\"]", "\'");
      }
      retBuilder.setResult(false).setCode(response_code.OTHER_ERROR)
          .setMessage(ByteString.copyFromUtf8(e.getClass() + " : " + errString));
    }
    trxExtBuilder.setResult(retBuilder);
    response.getWriter().println(Util.printTransactionExtention(trxExtBuilder.build(), visible));
  }
}
