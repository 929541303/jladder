package org.hum.nettyproxy.test.https_client.ca.impl;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStore.PasswordProtection;
import java.security.KeyStore.PrivateKeyEntry;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.security.UnrecoverableEntryException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.Extension;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.security.auth.x500.X500Principal;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.RFC4519Style;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import sun.security.x509.GeneralNames;

/**
 * @author hudaming
 * 参考 https://blog.csdn.net/cwjcsu/article/details/9217139
 */
public class CreateCert_forJava {

	static{
		try{
			Security.addProvider(new BouncyCastleProvider());
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	private static final String dnsName = "baidu.com";

	public static void main(String[] args) throws KeyStoreException, NoSuchAlgorithmException, CertificateException,
			FileNotFoundException, IOException, UnrecoverableEntryException {
		// 读取CA证书的JKS文件
		KeyStore caStore = KeyStore.getInstance("PKCS12");
		File caFile = new File("/Users/hudaming/Workspace/GitHub/netty-proxy/src/test/java/org/hum/nettyproxy/test/officaldemo/ca_and_cert/myca/rootca/server_cert.p12");
		caStore.load(new FileInputStream(caFile), "123456".toCharArray());

		// 给alice签发证书并存为server_cert.p12的文件
		PrivateKeyEntry caPrivateKey = (PrivateKeyEntry) caStore.getEntry("nickli", new PasswordProtection("123456".toCharArray()));
		String serverSubject = "CN=" + dnsName + ", OU=Hudaming, O=Hudaming, ST=Hudaming, C=CN";
		gen(caPrivateKey, serverSubject, "huming");
	}

	// 用KeyEntry形式存储一个私钥以及对应的证书，并把CA证书加入到它的信任证书列表里面。
	public static void store(PrivateKey key, Certificate cert, Certificate caCert, String name)
			throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException {
		KeyStore store = KeyStore.getInstance("PKCS12");
		store.load(null, null);
		System.out.println(validateChain(new Certificate[] { cert, caCert }));
		store.setKeyEntry("nickli", key, "123456".toCharArray(), new Certificate[] { cert });
		File file = new File("/Users/hudaming/Workspace/GitHub/netty-proxy/src/test/java/org/hum/nettyproxy/test/officaldemo/_20200720/cert4java.p12");
		if (file.exists() || file.createNewFile()) {
			store.store(new FileOutputStream(file), "123456".toCharArray());
		}
	}
	
    /**
     * 校验证书链代码
     * <pre>
     *   {@link sun.security.pkcs12.PKCS12KeyStore.validateChain}
     * </pre>
     * @param certChain
     * @return
     * @throws IOException
     */
    private static boolean validateChain(Certificate[] certChain) throws IOException
    {
        for (int i = 0; i < certChain.length-1; i++) {
        	// getRFC2253CanonicalName
        	/**
             * ============================================================
             * 
             * 
             * 
             * XXX 3.issuerDN和subjectDN的X500Name.names属性顺序就不同？怎么回事？什么原理，cert不是根据issuer生成的吗
             * 
             * 
             * 
             * ============================================================
        	 */
            X500Principal issuerDN = ((X509Certificate)certChain[i]).getIssuerX500Principal();
            X500Principal subjectDN = ((X509Certificate)certChain[i+1]).getSubjectX500Principal();
            /**
             * ============================================================
             * 
             * 
             * 
             * XXX 1.这个两个输出顺序不同，导致最终证书链无效
             * 
             * 
             * 
             * ============================================================
             */
//            System.out.println(new sun.security.x509.X500Name(issuerDN.getName()).getRFC2253CanonicalName());;
//            System.out.println(new sun.security.x509.X500Name(subjectDN.getName()).getRFC2253CanonicalName());;
            if (!(issuerDN.equals(subjectDN)))
                return false;
        }
        // Check for loops in the chain. If there are repeated certs,
        // the Set of certs in the chain will contain fewer certs than
        // the chain
        Set<Certificate> set = new HashSet<>(Arrays.asList(certChain));
        return set.size() == certChain.length;
    }

	// 用ke所代表的CA给subject签发证书，并存储到名称为name的jks文件里面。
	public static void gen(PrivateKeyEntry caPrivateKey, String serverSubject, String name) {
		try {
			sun.security.x509.X509CertImpl caCert = (sun.security.x509.X509CertImpl) caPrivateKey.getCertificate();
			KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
			kpg.initialize(2048);
			KeyPair keyPair = kpg.generateKeyPair();

			KeyStore store = KeyStore.getInstance("PKCS12");
			store.load(null, null);
        	/**
             * ============================================================
             * 
             * 
             * 
             * XXX 4.证书生成没有问题，现在看样子应该是我传值传的有问题吧？我传的是DN值，本身就是经过倒序后，现在需要试图拿到 caCert.info.names字段生成的Subject字符串
             * 
             * 
             * 
             * ============================================================
        	 */
			sun.security.x509.X500Name caX500Name = (sun.security.x509.X500Name) caCert.getSubjectDN();
			// 这里取了rfc2253，下面用的是rfc4519，两者格式能兼容？
			String issuer = caX500Name.getRFC2253Name();
			// 
			List<Extension> extensions = new ArrayList<>();
			sun.security.x509.SubjectAlternativeNameExtension ext = new sun.security.x509.SubjectAlternativeNameExtension();
			GeneralNames names = new GeneralNames();
			names.add(new sun.security.x509.GeneralName(new sun.security.x509.DNSName(dnsName)));
			ext.set("subject_name", names);
			extensions.add(ext);
			// 这个序列号要动态生成
			Certificate serverCert = generateV3(issuer, serverSubject, new BigInteger(System.currentTimeMillis() + ""),
					new Date(System.currentTimeMillis() - 1000 * 60 * 60 * 24),
					new Date(System.currentTimeMillis() + 1000L * 60 * 60 * 24 * 365 * 32), keyPair.getPublic(), // 待签名的公钥
					caPrivateKey.getPrivateKey()// CA的私钥
					, extensions);
			store(keyPair.getPrivate(), serverCert, caPrivateKey.getCertificate(), name);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static Certificate generateV3(String issuer, String subject, BigInteger serial, Date notBefore,
			Date notAfter, PublicKey publicKey, PrivateKey privKey, List<Extension> extensions)
			throws OperatorCreationException, CertificateException, IOException {
		X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(new X500Name(RFC4519Style.INSTANCE, issuer), serial, notBefore,
				notAfter, new X500Name(subject), publicKey);
		// 这里不要使用SHA1算法，Chrome浏览器会提示「NET::ERR_CERT_WEAK_SIGNATURE_ALGORITHM」意为使用了过期的加密算法
		ContentSigner sigGen = new JcaContentSignerBuilder("SHA256withRSA").setProvider("BC").build(privKey);
		// privKey是CA的私钥，publicKey是待签名的公钥，那么生成的证书就是被CA签名的证书。
		if (extensions != null) {
			for (Extension ext : extensions) {
				builder.addExtension(new ASN1ObjectIdentifier(ext.getId()), ext.isCritical(),
						ASN1Primitive.fromByteArray(ext.getValue()));
			}
		}
		X509CertificateHolder holder = builder.build(sigGen);
		CertificateFactory cf = CertificateFactory.getInstance("X.509");
		InputStream is1 = new ByteArrayInputStream(holder.toASN1Structure().getEncoded());
        /**
         * ============================================================
         * 
         * 
         * 
         * XXX 2.这里传入的issuer还没有变化，创建调用 X500Name.generateDN 时issuer顺序发生变化（根据rfc1779协议生成？names字段倒序生成，如果是正序就不存在问题，那到底应该是正序还是倒序？还是issuer和cert的配置规则不同导致？）
         * 
         * 调用链：
         * sun.security.x509.X500Name.generateDN()
         * sun.security.x509.X509CertInfo.parse()
         * sun.security.x509.X509CertInfo.X509CertInfo()
         * sun.security.x509.X509CertImpl.parse()
         * sun.security.x509.X509CertImpl.X509CertImpl()
         * sun.security.provider.X509Factory.engineGenerateCertificate()
         * java.security.cert.CertificateFactory.generateCertificate()
         * org.hum.nettyproxy.test.https_client.ca.impl.CreateCert_forJava.generateV3()
         * 
         * 
         * 
         * ============================================================
         */
		X509Certificate theCert = (X509Certificate) cf.generateCertificate(is1);
		is1.close();
		return theCert;
	}
}
