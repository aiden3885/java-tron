package org.tron.core.actuator;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import ethereum.ckzg4844.CKZG4844JNI;
import ethereum.ckzg4844.CKZGException;
import ethereum.ckzg4844.KZG4844;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.encoders.Hex;
import org.tron.core.ChainBaseManager;
import org.tron.core.Constant;
import org.tron.core.capsule.ContractCapsule;
import org.tron.core.db.TransactionContext;
import org.tron.core.exception.ContractExeException;
import org.tron.core.exception.ContractValidateException;
import org.tron.core.vm.config.VMConfig;
import org.tron.protos.Protocol.Transaction;
import org.tron.protos.contract.SmartContractOuterClass.BlobContract;

import java.util.Arrays;
import java.util.List;

@Slf4j(topic = "actuator")
public class BlobActuator implements  Actuator2 {

    private ChainBaseManager chainBaseManager;

    private VMActuator vmActuator = new VMActuator(false);

    private BlobContract blobContract;

    public BlobActuator() {}


    @Override
    public void validate(Object object) throws ContractValidateException {
        if (!VMConfig.allow4844()) {
            throw new ContractValidateException("Not support blob transaction,"
                    + " need to be opened by the committee");
        }
        TransactionContext context = (TransactionContext) object;
        Transaction trx = context.getTrxCap().getInstance();
        Any any =  trx.getRawData().getContract(0).getParameter();

        blobContract = ContractCapsule.getBlobContractFromTransaction(trx);
        if (blobContract == null) {
            throw new ContractValidateException(ActuatorConstant.CONTRACT_NOT_EXIST);
        }

        // Ensure the blob fee cap satisfies the minimum blob gas price
        if (blobContract.getBlobFeeCap() < chainBaseManager.getDynamicPropertiesStore().getBlobTxMinBlobEnergyPrice()) {
            throw new ContractValidateException(String.format("blob fee cap %d, minimum needed %d", blobContract.getBlobFeeCap(), chainBaseManager.getDynamicPropertiesStore().getBlobTxMinBlobEnergyPrice()));
        }

       BlobContract.BlobTxSidecar sidecar = blobContract.getSidecar();
        List<ByteString> blobHashes = blobContract.getBlobHashesList();

        // Ensure the number of items in the blob transaction and various side
        // data match up before doing any expensive validations
        if (blobHashes.isEmpty()) {
            throw new ContractValidateException("blobless blob transaction");
        }

        if (blobHashes.size() > Constant.MAX_BLOBS_PER_BLOCK) {
            throw new ContractValidateException(String.format("too many blobs in transaction: have %d, permitted %d", blobHashes.size(), Constant.MAX_BLOBS_PER_BLOCK));
        }

        //validate sideCars
        validateSidecars(blobHashes, sidecar);

        vmActuator.validate(object);
    }

    private void validateSidecars(List<ByteString> blobHashes, BlobContract.BlobTxSidecar sidecar) throws ContractValidateException {
        if (sidecar.getBlobsCount() != blobHashes.size()) {
            throw new ContractValidateException(String.format("invalid number of %d blobs compare to %d blob hashes", sidecar.getBlobsCount(), blobHashes.size()));
        }

        if (sidecar.getCommitmentsCount() != blobHashes.size()) {
            throw new ContractValidateException(String.format("invalid number of %d commitments compare to %d blob hashes", sidecar.getBlobsCount(), blobHashes.size()))
        }

        if (sidecar.getProofsCount() != blobHashes.size()) {
            throw new ContractValidateException(String.format("invalid number of %d proofs compare to %d blob hashes", sidecar.getProofsCount(), blobHashes.size()))
        }

        // Blob quantities match up, validate that the provers match with the
        // transaction hash before getting to the cryptography
        for (int i = 0; i < blobHashes.size(); i++) {
            byte[] blobHashBytes = blobHashes.get(i).toByteArray();
            byte[] computed =  KZG4844.calcBlobHashV1(sidecar.getCommitments(i).toByteArray());
            if (!Arrays.equals(blobHashBytes, computed)) {
                throw new ContractValidateException(String.format("blob %d, computed hash %s mismatches transaction one %s",i, Hex.toHexString(computed), Hex.toHexString(blobHashBytes)));
            }
        }

        // Blob commitments match with the hashes in the transaction, verify the
        // blobs themselves via KZG
        for (int i = 0; i < blobHashes.size(); i++) {
            try {
                if (!CKZG4844JNI.verifyBlobKzgProof(blobHashes.get(i).toByteArray(), sidecar.getCommitments(i).toByteArray(), sidecar.getProofs(i).toByteArray())) {
                    throw new ContractValidateException(String.format("invalid blob %d", i));
                }
            }
            catch (CKZGException e) {
                throw new ContractValidateException(String.format("invalid blob %d", i));
            }
        }
    }

    @Override
    public void execute(Object object) throws ContractExeException {
        vmActuator.execute(object);
    }


    public BlobActuator setChainBaseManager(ChainBaseManager chainBaseManager) {
        this.chainBaseManager = chainBaseManager;
        return this;
    }
}
