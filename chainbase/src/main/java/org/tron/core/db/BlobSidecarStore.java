package org.tron.core.db;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.tron.core.capsule.BlobSidecarCapsule;

@Slf4j(topic = "DB")
@Component
public class BlobSidecarStore extends TronStoreWithRevoking<BlobSidecarCapsule> {

  @Autowired
  private BlobSidecarStore(@Value("blob-sidecar") String dbName) {
    super(dbName);
  }
}
