package org.tron.common.runtime.vm;

import static org.tron.common.utils.ByteUtil.longTo32Bytes;

import java.util.Collections;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.encoders.Hex;
import org.junit.Assert;
import org.junit.Test;
import org.tron.common.runtime.TVMTestResult;
import org.tron.common.runtime.TvmTestUtils;
import org.tron.common.utils.WalletUtil;
import org.tron.common.utils.client.utils.AbiUtil;
import org.tron.core.Wallet;
import org.tron.core.exception.ContractExeException;
import org.tron.core.exception.ContractValidateException;
import org.tron.core.exception.ReceiptCheckErrException;
import org.tron.core.exception.VMIllegalException;
import org.tron.core.vm.config.ConfigLoader;
import org.tron.core.vm.config.VMConfig;
import org.tron.protos.Protocol;

@Slf4j
public class BlobBaseFeeTest extends VMTestBase {

  @Test
  public void testBlobBaseFee() throws ContractExeException, ReceiptCheckErrException,
      VMIllegalException, ContractValidateException {

    ConfigLoader.disable = true;
    VMConfig.initAllowTvmBlob(1);
    VMConfig.initAllowTvmTransferTrc10(1);
    VMConfig.initAllowTvmShangHai(1);
    VMConfig.initAllowTvmConstantinople(1);
    VMConfig.initAllowTvmSolidity059(1);
    VMConfig.initAllowTvmIstanbul(1);
    manager.getDynamicPropertiesStore().saveBlobFee(100);

    String contractName = "testBlobBaseFee";
    byte[] address = Hex.decode(Wallet.getAddressPreFixString()
        + "abd4b9367799eaa3197fecb144eb71de1e049abc");
    String abi = "[]";
    String factoryCode = "608060405234801561000f575f80fd5b50d3801"
        + "561001b575f80fd5b50d28015610027575f80fd5b5060898061003"
        + "45f395ff3fe6080604052348015600e575f80fd5b50d3801560195"
        + "75f80fd5b50d280156024575f80fd5b5060043610603c575f3560e"
        + "01c8063143e711d146040575b5f80fd5b4a6040519081526020016"
        + "0405180910390f3fea26474726f6e582212203ff1eb74d454a3454"
        + "ca9d233baadab658e201c548f729329cabd36b45939a24b64736f6"
        + "c63430008180033";
    long value = 0;
    long feeLimit = 100000000;
    long consumeUserResourcePercent = 0;

    // deploy contract
    Protocol.Transaction trx = TvmTestUtils.generateDeploySmartContractAndGetTransaction(
        contractName, address, abi, factoryCode, value, feeLimit, consumeUserResourcePercent,
        null);
    byte[] factoryAddress = WalletUtil.generateContractAddress(trx);
    runtime = TvmTestUtils.processTransactionAndReturnRuntime(trx, rootRepository, null);
    Assert.assertNull(runtime.getRuntimeError());

    String methodByAddr = "getBlobbasefee()";
    String hexInput =
        AbiUtil.parseMethod(methodByAddr, Collections.singletonList(""));

    TVMTestResult result = TvmTestUtils
        .triggerContractAndReturnTvmTestResult(Hex.decode(OWNER_ADDRESS),
            factoryAddress, Hex.decode(hexInput), 0, feeLimit, manager, null);
    byte[] returnValue = result.getRuntime().getResult().getHReturn();
    Assert.assertNull(result.getRuntime().getRuntimeError());
    Assert.assertArrayEquals(returnValue,
        longTo32Bytes(manager.getDynamicPropertiesStore().getBlobFee()));
  }
}
