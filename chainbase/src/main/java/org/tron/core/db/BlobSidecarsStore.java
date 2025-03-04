package org.tron.core.db;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.tron.core.capsule.BlobSidecarsCapsule;

@Slf4j(topic = "DB")
@Component
public class BlobSidecarsStore extends TronStoreWithRevoking<BlobSidecarsCapsule> {

  @Autowired
  private BlobSidecarsStore(@Value("blob-sidecar") String dbName) {
    super(dbName);
  }
}
