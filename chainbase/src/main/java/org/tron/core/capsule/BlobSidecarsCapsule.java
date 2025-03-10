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

import com.google.protobuf.ByteString;
import com.google.protobuf.CodedInputStream;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.tron.common.overlay.message.Message;
import org.tron.core.exception.BadItemException;
import org.tron.protos.Protocol.BlobSidecars;

@Slf4j(topic = "capsule")
public class BlobSidecarsCapsule implements ProtoCapsule<BlobSidecars> {

  private BlobSidecars blobSidecars;

  /**
   * constructor BlobSidecarCapsule.
   */
  public BlobSidecarsCapsule(BlobSidecars blobSidecars) {
    this.blobSidecars = blobSidecars;
  }

  /**
   * get blobSidecar from bytes data.
   */
  public BlobSidecarsCapsule(byte[] data) throws BadItemException {
    try {
      this.blobSidecars = BlobSidecars.parseFrom(Message.getCodedInputStream(data));
    } catch (Exception e) {
      throw new BadItemException("BlobSidecar proto data parse exception");
    }
  }

  public BlobSidecarsCapsule(CodedInputStream codedInputStream) throws BadItemException {
    try {
      this.blobSidecars = BlobSidecars.parseFrom(codedInputStream);
    } catch (IOException e) {
      throw new BadItemException("BlobSidecar proto data parse exception");
    }
  }

  public static byte[] createDbKey(long blockNum, ByteString blockHash) {
    return (blockNum + "-" + blockHash).getBytes();
  }

  @Override
  public byte[] getData() {
    return this.blobSidecars.toByteArray();
  }

  @Override
  public BlobSidecars getInstance() {
    return this.blobSidecars;
  }
}
