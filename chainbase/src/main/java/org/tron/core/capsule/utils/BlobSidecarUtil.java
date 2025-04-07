/*
 * java-tron is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * java-tron is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.tron.core.capsule.utils;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.tron.common.crypto.ckzg4844.CKZG4844JNI;
import org.tron.common.crypto.ckzg4844.KZG4844;
import org.bouncycastle.util.encoders.Hex;
import org.tron.core.capsule.BlockCapsule;
import org.tron.core.capsule.ContractCapsule;
import org.tron.core.capsule.TransactionCapsule;
import org.tron.core.exception.BadBlockException;
import org.tron.core.exception.ContractValidateException;
import org.tron.protos.Protocol;
import org.tron.protos.Protocol.BlobSidecar;
import org.tron.protos.Protocol.BlobTxSidecar;
import org.tron.protos.Protocol.Transaction;
import org.tron.protos.Protocol.Transaction.Contract.ContractType;
import org.tron.protos.contract.SmartContractOuterClass.BlobContract;

import static org.tron.common.math.StrictMathWrapper.addExact;
import static org.tron.core.Constant.MAX_BLOBS_PER_BLOCK;

public class BlobSidecarUtil {

  private static void validateSidecars(List<ByteString> blobHashes, BlobTxSidecar sidecar)
      throws ContractValidateException {
    if (sidecar.getBlobsCount() != blobHashes.size()) {
      throw new ContractValidateException(
          String.format("invalid number of %d blobs compare to %d blob hashes",
              sidecar.getBlobsCount(), blobHashes.size()));
    }

    if (sidecar.getCommitmentsCount() != blobHashes.size()) {
      throw new ContractValidateException(
          String.format("invalid number of %d commitments compare to %d blob hashes",
              sidecar.getCommitmentsCount(), blobHashes.size()));
    }

    if (sidecar.getProofsCount() != blobHashes.size()) {
      throw new ContractValidateException(
          String.format("invalid number of %d proofs compare to %d blob hashes",
              sidecar.getProofsCount(), blobHashes.size()));
    }

    // Blob quantities match up, validate that the provers match with the
    // transaction hash before getting to the cryptography
    for (int i = 0; i < blobHashes.size(); i++) {
      byte[] blobHashBytes = blobHashes.get(i).toByteArray();
      byte[] computed =  KZG4844.calcBlobHashV1(sidecar.getCommitments(i).toByteArray());
      if (!Arrays.equals(blobHashBytes, computed)) {
        throw new ContractValidateException(
            String.format("blob %d, computed hash %s mismatches transaction one %s",
                i, Hex.toHexString(computed), Hex.toHexString(blobHashBytes)));
      }
    }

    // Blob commitments match with the hashes in the transaction, verify the
    // blobs themselves via KZG
    for (int i = 0; i < blobHashes.size(); i++) {
      try {
        if (!CKZG4844JNI.verifyBlobKzgProof(
            sidecar.getBlobs(i).toByteArray(),
            sidecar.getCommitments(i).toByteArray(),
            sidecar.getProofs(i).toByteArray())) {
          throw new ContractValidateException(String.format("invalid blob %d", i));
        }
      } catch (RuntimeException e) {
        throw new ContractValidateException(String.format("invalid blob %d", i));
      }
    }
  }

  public static int preValidateBlobTx(TransactionCapsule trx, int packedCount)
      throws ContractValidateException {
    if (!trx.isBlobTransaction()) {
      return 0;
    }
    BlobContract blobContract = ContractCapsule.getBlobContractFromTransaction(trx.getInstance());
    if (blobContract.getSidecar().getBlobsCount() == 0) {
      throw new ContractValidateException("missing sidecar in blob transaction");
    }
    // Ensure the number of items in the blob transaction and various side
    // data match up before doing any expensive validations
    int blobCount = blobContract.getBlobHashesCount();
    if (blobCount == 0) {
      throw new ContractValidateException("blobless blob transaction");
    }
    int totalBlobCount = addExact(packedCount, blobCount);
    if (totalBlobCount > MAX_BLOBS_PER_BLOCK) {
      return totalBlobCount;
    }

    //validate sideCars
    validateSidecars(blobContract.getBlobHashesList(), blobContract.getSidecar());

    // return blob
    return blobContract.getBlobHashesList().size();
  }

  public static void validateBlockBlobTx(BlockCapsule block)
      throws BadBlockException, ContractValidateException {
    List<BlobSidecar> sidecarsList = block.getInstance().getBlobSidecarList();
    if (sidecarsList.isEmpty()) {
      return;
    }

    List<TransactionCapsule> txs = block.getTransactions();
    long blobTxCount = txs.stream().filter(TransactionCapsule::isBlobTransaction).count();
    if (sidecarsList.size() != blobTxCount) {
      throw new BadBlockException(String.format(
          "%d blob sidecars in block, %d blob transactions, not match",
          sidecarsList.size(), blobTxCount));
    }

    int totalBlobCount = 0;
    for (BlobSidecar blobSidecar: sidecarsList) {
      validateBlockBlobSidecar(blobSidecar, block.getNum(), block.getBlockId().getBytes());
      totalBlobCount = addExact(totalBlobCount, blobSidecar.getSidecar().getBlobsCount());
    }

    if (totalBlobCount > MAX_BLOBS_PER_BLOCK) {
      throw new BadBlockException(
          String.format("too many blobs in transaction: have %d, permitted %d",
              totalBlobCount, MAX_BLOBS_PER_BLOCK));
    }

    List<TransactionCapsule> blobTxs = new ArrayList<>();
    List<Integer> blobTxIndexes = new ArrayList<>();
    for (int i = 0; i < txs.size(); i++) {
      TransactionCapsule curTx = txs.get(i);
      if (curTx.isBlobTransaction()) {
        blobTxs.add(curTx);
        blobTxIndexes.add(i);
      }
    }

    for (int i = 0; i < blobTxs.size(); i++) {
      TransactionCapsule curTx = blobTxs.get(i);
      if (!sidecarsList.get(i).getTxHash().equals(curTx.getTransactionId().getByteString())) {
        throw new BadBlockException("sidecar's TxHash mismatch with expected transaction");
      }
      if (sidecarsList.get(i).getTxIndex() != blobTxIndexes.get(i)) {
        throw new BadBlockException("sidecar's TxIndex mismatch with expected transaction");
      }
      BlobContract blobContract = ContractCapsule.getBlobContractFromTransaction(curTx.getInstance());
      if (blobContract.getSidecar().getBlobsCount() != 0) {
        throw new BadBlockException("tx in block should not have blob");
      }
      validateSidecars(blobContract.getBlobHashesList(), sidecarsList.get(i).getSidecar());
    }
  }

  public static void validateBlockBlobSidecar(
      BlobSidecar blobSidecar, long blockNum, byte[] blockHash)
      throws BadBlockException {
    if (blobSidecar.getBlockNumber() != blockNum) {
      throw new BadBlockException("BlobSidecar with wrong block number");
    }
    if (!Arrays.equals(blobSidecar.getBlockHash().toByteArray(), blockHash)) {
      throw new BadBlockException("BlobSidecar with wrong block hash");
    }
    if (blobSidecar.getSidecar().getBlobsCount()
        != blobSidecar.getSidecar().getCommitmentsCount()) {
      throw new BadBlockException("BlobSidecar has wrong commitment count");
    }
    if (blobSidecar.getSidecar().getBlobsCount()
        != blobSidecar.getSidecar().getProofsCount()) {
      throw new BadBlockException("BlobSidecar has wrong proof count");
    }
  }

  public static Transaction getTransactionWithoutSidecar(Transaction transaction) {
    if (transaction.getRawData().getContract(0).getType() != ContractType.BlobContract) {
      return transaction;
    }
    Protocol.Transaction.Contract contract = transaction.getRawData().getContract(0);
    BlobContract blobContract = ContractCapsule.getBlobContractFromTransaction(transaction);
    BlobContract blobContractWithoutBlob = blobContract.toBuilder().clearSidecar().build();

    return Protocol.Transaction.newBuilder()
        .mergeFrom(transaction)
        .setRawData(
            Protocol.Transaction.raw
                .newBuilder()
                .mergeFrom(transaction.getRawData())
                .setContract(
                    0,
                    Protocol.Transaction.Contract.newBuilder()
                        .mergeFrom(contract)
                        .setParameter(Any.pack(blobContractWithoutBlob))))
        .build();
  }
}
