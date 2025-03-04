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

package org.tron.core.capsule;

import com.google.protobuf.CodedInputStream;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.tron.common.overlay.message.Message;
import org.tron.core.exception.BadItemException;
import org.tron.protos.Protocol.BlobSidecar;

@Slf4j(topic = "capsule")
public class BlobSidecarCapsule implements ProtoCapsule<BlobSidecar> {

  private BlobSidecar blobSidecar;

  /**
   * constructor BlobSidecarCapsule.
   */
  public BlobSidecarCapsule(BlobSidecar blobSidecar) {
    this.blobSidecar = blobSidecar;
  }

  /**
   * get blobSidecar from bytes data.
   */
  public BlobSidecarCapsule(byte[] data) throws BadItemException {
    try {
      this.blobSidecar = BlobSidecar.parseFrom(Message.getCodedInputStream(data));
    } catch (Exception e) {
      throw new BadItemException("BlobSidecar proto data parse exception");
    }
  }

  public BlobSidecarCapsule(CodedInputStream codedInputStream) throws BadItemException {
    try {
      this.blobSidecar = BlobSidecar.parseFrom(codedInputStream);
    } catch (IOException e) {
      throw new BadItemException("BlobSidecar proto data parse exception");
    }
  }

  public byte[] createDbKey() {
    return (blobSidecar.getBlockNumber() + "-" + blobSidecar.getTxIndex()).getBytes();
  }

  @Override
  public byte[] getData() {
    return this.blobSidecar.toByteArray();
  }

  @Override
  public BlobSidecar getInstance() {
    return this.blobSidecar;
  }
}
