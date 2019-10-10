package org.hum.nettyproxy.test.officaldemo.https_server;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

public class HttpsKeyStore {

	public static InputStream getKeyStoreStream() {
		InputStream inStream = null;
		try {
			inStream = new FileInputStream(Arguments.keystorePath);
		} catch (FileNotFoundException e) {
			System.out.println("读取密钥文件失败 " + e);
		}
		return inStream;
	}

	public static char[] getCertificatePassword() {
		return Arguments.certificatePassword.toCharArray();
	}

	public static char[] getKeyStorePassword() {
		return Arguments.keystorePassword.toCharArray();
	}
}

class Arguments {
	public static String keystorePath = "d:/keystore.p12";
	public static String certificatePassword = "111111";
	public static String keystorePassword = "111111";
}
