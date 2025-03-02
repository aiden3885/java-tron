package ethereum.ckzg4844;

import org.tron.common.utils.Sha256Hash;

public class KZG4844 {


    public static byte[] calcBlobHashV1(byte[] commitment) {
        byte[] hash = Sha256Hash.hash(true, commitment);
        hash[0] = 0x01;
        return hash;
    }
}
