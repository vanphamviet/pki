/**
* OpenCPS PKI is the open source PKI Integration software
* Copyright (C) 2016-present OpenCPS community

* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU Affero General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* any later version.

* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU Affero General Public License for more details.
* You should have received a copy of the GNU Affero General Public License
* along with this program. If not, see <http://www.gnu.org/licenses/>
*/
package org.opencps.pki;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.Signature;
import java.security.SignatureException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;

import com.itextpdf.text.pdf.PdfDictionary;
import com.itextpdf.text.pdf.security.CertificateUtil;
import com.itextpdf.text.pdf.security.DigestAlgorithms;
import com.itextpdf.text.pdf.security.ExternalDigest;
import com.itextpdf.text.pdf.security.ExternalSignatureContainer;
import com.itextpdf.text.pdf.security.MakeSignature.CryptoStandard;

import com.itextpdf.text.pdf.security.PdfPKCS7;
import com.itextpdf.text.pdf.security.TSAClient;
import com.itextpdf.text.pdf.security.TSAClientBouncyCastle;

/**
 * Produces a signed data from client. Useful for deferred signing
 * 
 * @author Nguyen Van Nguyen <nguyennv@iwayvietnam.com>
 */
public class Pkcs7GenerateSignatureContainer implements ExternalSignatureContainer {

    private byte[] signature;
    
    private PdfSigner signer;

    /**
     * Constructor
     */
    public Pkcs7GenerateSignatureContainer(PdfSigner signer, byte[] signature) {
        this.signer = signer;
        this.signature = signature;
    }

    /**
     * Modifies the signature dictionary to suit the container. At least the keys PdfName.FILTER and 
     * PdfName.SUBFILTER will have to be set.
     * @param signDic the signature dictionary
     */
    @Override
    public void modifySigningDictionary(PdfDictionary pd) {
    }

    /**
     * Produces the container with the signature.
     * @param data the data to sign
     * @return a container with the signature and other objects, like CRL and OCSP. The container will generally be a PKCS7 one.
     * @throws GeneralSecurityException 
     */
    @Override
    public byte[] sign(InputStream is) throws GeneralSecurityException {
        X509Certificate cert = signer.getCertificate();
        RSAPublicKey rsaKey = (RSAPublicKey) cert.getPublicKey();
        Integer keyLength = rsaKey.getModulus().bitLength() / 8;

        if (keyLength != signature.length) {
            throw new SignatureException("Signature length not correct");
        }
        
        ExternalDigest digest = signer.getExternalDigest();

        byte[] digestHash = null;
        try {
            digestHash = DigestAlgorithms.digest(is, digest.getMessageDigest(signer.getHashAlgorithm().toString()));
        } catch (IOException e) {
            throw new SignatureException(e.getMessage(), e);
        }

        PdfPKCS7 sgn = new PdfPKCS7(null, new Certificate[] { cert }, signer.getHashAlgorithm().toString(), null, digest, false);
        byte[] sh = sgn.getAuthenticatedAttributeBytes(digestHash, null, null, CryptoStandard.CMS);
        Signature sig = Signature.getInstance(signer.getHashAlgorithm().toString() + "with" + cert.getPublicKey().getAlgorithm());
        sig.initVerify(cert.getPublicKey());
        sig.update(sh);
        if (!sig.verify(signature)) {
            throw new SignatureException("Signature is not correct");
        }

        TSAClient tsaClient = null;
        String tsaUrl = CertificateUtil.getTSAURL(cert);
        if (tsaUrl != null) {
            tsaClient = new TSAClientBouncyCastle(tsaUrl);
        }
        
        sgn.setExternalDigest(signature, null, cert.getPublicKey().getAlgorithm());
        return sgn.getEncodedPKCS7(digestHash, tsaClient, null, null, CryptoStandard.CMS);
    }

}
