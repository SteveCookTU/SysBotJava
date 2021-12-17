package me.ezpzstreamz.sysbotjava.util;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public class Util {

    private static final int[] blockPosition = {
            0, 1, 2, 3,
            0, 1, 3, 2,
            0, 2, 1, 3,
            0, 3, 1, 2,
            0, 2, 3, 1,
            0, 3, 2, 1,
            1, 0, 2, 3,
            1, 0, 3, 2,
            2, 0, 1, 3,
            3, 0, 1, 2,
            2, 0, 3, 1,
            3, 0, 2, 1,
            1, 2, 0, 3,
            1, 3, 0, 2,
            2, 1, 0, 3,
            3, 1, 0, 2,
            2, 3, 0, 1,
            3, 2, 0, 1,
            1, 2, 3, 0,
            1, 3, 2, 0,
            2, 1, 3, 0,
            3, 1, 2, 0,
            2, 3, 1, 0,
            3, 2, 1, 0,
            0, 1, 2, 3,
            0, 1, 3, 2,
            0, 2, 1, 3,
            0, 3, 1, 2,
            0, 2, 3, 1,
            0, 3, 2, 1,
            1, 0, 2, 3,
            1, 0, 3, 2
    };

    private static final int[] blockPositionInvert =
            {0, 1, 2, 4, 3, 5, 6, 7, 12, 18, 13, 19, 8, 10, 14, 20, 16, 22, 9, 11, 15, 21, 17, 23,
                    0, 1, 2, 4, 3, 5, 6, 7};

    private static byte[] shuffleArray(byte[] data, int sv, int blockSize) {
        byte[] sdata = data.clone();
        int i = (sv * 4) & 0xFF;
        int start = 8;
        for (int b = 0; b < 4; b++) {
            int ofs = blockPosition[i + b];
            int pos = start + (blockSize * ofs);
            byte[] toCopy = Arrays.copyOfRange(data, start + (blockSize * ofs), start + (blockSize * ofs) + blockSize);
            byte[] temp = new byte[0x158];
            System.arraycopy(sdata, 0, temp, 0, start + (blockSize * b));
            System.arraycopy(toCopy, 0, temp, start + (blockSize * b), toCopy.length);
            System.arraycopy(sdata, start + (blockSize * b) + blockSize, temp, start + (blockSize * b) + toCopy.length,
                    sdata.length - (start + (blockSize * b) + blockSize));
            sdata = temp;
        }
        return sdata;
    }

    private static int crypt(byte[] data, int ec, int index) {
        long t1 = 0x41c64e6dL * ec;
        long t2 = 0x6073 + t1;
        int ec2 = (int) (t2);
        data[index] ^= ec2 >> 16 & 0xFF;
        data[index + 1] ^= ec2 >> 24 & 0xFF;

        return ec2;
    }

    private static void cryptArray(byte[] data, int ec, int start, int end) {
        int ec2 = ec;
        for(int i = start; i < end; i += 2) {
            ec2 = crypt(data, ec2, i);
        }
    }

    private static void cryptPKM(byte[] data, int ec, int start, int blockSize) {
        int end = (4  * blockSize) + start;
        cryptArray(data, ec, start, end);
        if(data.length > end)
            cryptArray(data, ec, end, data.length);
    }

    public static byte[] encryptPb8(byte[] data) {
        ByteBuffer bb = ByteBuffer.wrap(data, 0, 4);
        bb.order(ByteOrder.LITTLE_ENDIAN);
        int EC = bb.getInt();
        int SV = EC >> 13 & 31;
        byte[] ekm = shuffleArray(data, blockPositionInvert[SV], 80);
        cryptPKM(ekm, EC, 8, 80);
        return ekm;
    }

    //Thank you stack overflow https://stackoverflow.com/questions/9655181/how-to-convert-a-byte-array-to-a-hex-string-in-java?page=1&tab=votes#tab-top
    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }

}
