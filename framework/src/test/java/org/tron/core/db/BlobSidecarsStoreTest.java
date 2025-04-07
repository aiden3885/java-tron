package org.tron.core.db;

import javax.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;
import org.tron.common.BaseTest;
import org.tron.core.Constant;
import org.tron.core.capsule.BlobSidecarsCapsule;
import org.tron.core.config.args.Args;
import org.tron.core.exception.BadItemException;
import org.tron.core.exception.ItemNotFoundException;
import org.tron.protos.Protocol;

@Slf4j
public class BlobSidecarsStoreTest extends BaseTest {

  @Resource
  private BlobSidecarsStore blobSidecarsStore;

  static {
    Args.setParam(new String[]{"--output-directory", dbPath()},
        Constant.TEST_CONF);
  }

  private BlobSidecarsCapsule getBlobSidecarsCapsule(long blockNumber) {
    return new BlobSidecarsCapsule(
        Protocol.BlobSidecars.newBuilder()
            .addBlobSidecar(Protocol.BlobSidecar.newBuilder().setBlockNumber(blockNumber).build())
            .build());
  }

  @Test
  public void testPut() {
    long blockNum = 1;
    BlobSidecarsCapsule blobSidecarsCapsule = getBlobSidecarsCapsule(blockNum);
    byte[] dbKey = BlobSidecarsCapsule.createDbKey(blockNum);
    blobSidecarsStore.put(dbKey, blobSidecarsCapsule);
    Assert.assertTrue(blobSidecarsStore.has(dbKey));
  }

  @Test
  public void testGet() {
    long blockNum = 1;
    BlobSidecarsCapsule blobSidecarsCapsule = getBlobSidecarsCapsule(blockNum);
    byte[] dbKey = BlobSidecarsCapsule.createDbKey(blockNum);
    blobSidecarsStore.put(dbKey, blobSidecarsCapsule);

    try {
      BlobSidecarsCapsule blobSidecarsCapsule1 = blobSidecarsStore.get(dbKey);
      Assert.assertEquals(
          blockNum, blobSidecarsCapsule1.getInstance().getBlobSidecar(0).getBlockNumber());
    } catch (ItemNotFoundException | BadItemException e) {
      e.printStackTrace();
    }
  }

  @Test
  public void testDelete() {
    long blockNum = 1;
    BlobSidecarsCapsule blobSidecarsCapsule = getBlobSidecarsCapsule(blockNum);
    byte[] dbKey = BlobSidecarsCapsule.createDbKey(blockNum);
    blobSidecarsStore.put(dbKey, blobSidecarsCapsule);
    Assert.assertTrue(blobSidecarsStore.has(dbKey));

    blobSidecarsStore.delete(dbKey);
    Assert.assertFalse(blobSidecarsStore.has(dbKey));
  }

}
