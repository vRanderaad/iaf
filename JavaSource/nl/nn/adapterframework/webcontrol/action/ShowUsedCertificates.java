/*
 * $Log: ShowUsedCertificates.java,v $
 * Revision 1.1  2007-12-28 12:17:51  europe\L190409
 * first version
 *
 */
package nl.nn.adapterframework.webcontrol.action;

import java.io.IOException;
import java.net.URL;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Enumeration;
import java.util.Iterator;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import nl.nn.adapterframework.core.Adapter;
import nl.nn.adapterframework.core.HasSender;
import nl.nn.adapterframework.core.IPipe;
import nl.nn.adapterframework.core.IReceiver;
import nl.nn.adapterframework.core.ISender;
import nl.nn.adapterframework.core.PipeLine;
import nl.nn.adapterframework.ftp.FtpSender;
import nl.nn.adapterframework.http.HttpSender;
import nl.nn.adapterframework.http.WebServiceSender;
import nl.nn.adapterframework.pipes.MessageSendingPipe;
import nl.nn.adapterframework.util.ClassUtils;
import nl.nn.adapterframework.util.CredentialFactory;
import nl.nn.adapterframework.util.RunStateEnum;
import nl.nn.adapterframework.util.XmlBuilder;

import org.apache.commons.lang.StringUtils;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

/**
 * Shows the used certificate.
 * 
 * @author  Peter Leeuwenburgh
 * @since	4.8
 * @version Id 
 */

public final class ShowUsedCertificates extends ActionBase {
	public static final String version = "$RCSfile: ShowUsedCertificates.java,v $ $Revision: 1.1 $ $Date: 2007-12-28 12:17:51 $";

	protected void addCertificateInfo(XmlBuilder certElem, final URL url, final String password, String keyStoreType, String prefix) {
		try {
			KeyStore keystore  = KeyStore.getInstance(keyStoreType);
			keystore.load(url.openStream(), password != null ? password.toCharArray(): null);
			if (log.isInfoEnabled()) {
				Enumeration aliases = keystore.aliases();
				while (aliases.hasMoreElements()) {
					String alias = (String)aliases.nextElement();
					XmlBuilder infoElem = new XmlBuilder("info");
					infoElem.setCdataValue(prefix+" '" + alias + "':");
					certElem.addSubElement(infoElem);
					Certificate trustedcert = keystore.getCertificate(alias);
					if (trustedcert != null && trustedcert instanceof X509Certificate) {
						X509Certificate cert = (X509Certificate)trustedcert;
						infoElem = new XmlBuilder("info");
						infoElem.setCdataValue("  Subject DN: " + cert.getSubjectDN());
						certElem.addSubElement(infoElem);
						infoElem = new XmlBuilder("info");
						infoElem.setCdataValue("  Signature Algorithm: " + cert.getSigAlgName());
						certElem.addSubElement(infoElem);
						infoElem = new XmlBuilder("info");
						infoElem.setCdataValue("  Valid from: " + cert.getNotBefore());
						certElem.addSubElement(infoElem);
						infoElem = new XmlBuilder("info");
						infoElem.setCdataValue("  Valid until: " + cert.getNotAfter());
						certElem.addSubElement(infoElem);
						infoElem = new XmlBuilder("info");
						infoElem.setCdataValue("  Issuer: " + cert.getIssuerDN());
						certElem.addSubElement(infoElem);
					}
				}
			}
		} catch (Exception e) {
			XmlBuilder infoElem = new XmlBuilder("info");
			infoElem.setCdataValue("*** ERROR ***");
			certElem.addSubElement(infoElem);
		}
	}

	public ActionForward execute(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {

		// Initialize action
		initAction(request);

		if (null==config) {
			return (mapping.findForward("noconfig"));
		}

		XmlBuilder adapters=new XmlBuilder("registeredAdapters");
		for(int j=0; j<config.getRegisteredAdapters().size(); j++) {
			Adapter adapter = (Adapter)config.getRegisteredAdapter(j);

			XmlBuilder adapterXML=new XmlBuilder("adapter");
			adapters.addSubElement(adapterXML);
		
			RunStateEnum adapterRunState = adapter.getRunState();
			
			adapterXML.addAttribute("name",adapter.getName());
		
			Iterator recIt=adapter.getReceiverIterator();
			if (recIt.hasNext()){
				XmlBuilder receiversXML=new XmlBuilder("receivers");
				while (recIt.hasNext()){
					IReceiver receiver=(IReceiver) recIt.next();
					XmlBuilder receiverXML=new XmlBuilder("receiver");
					receiversXML.addSubElement(receiverXML);
	
					RunStateEnum receiverRunState = receiver.getRunState();
					 
					receiverXML.addAttribute("name",receiver.getName());

					if (receiver instanceof HasSender) {
						ISender sender = ((HasSender) receiver).getSender();
						if (sender != null) { 
							receiverXML.addAttribute("senderName", sender.getName());
						}
					}
				}
				adapterXML.addSubElement(receiversXML); 
			}

			// make list of pipes to be displayed in configuration status
			XmlBuilder pipesElem = new XmlBuilder("pipes");
			adapterXML.addSubElement(pipesElem);
			PipeLine pipeline = adapter.getPipeLine();
			for (int i=0; i<pipeline.getPipes().size(); i++) {
				IPipe pipe = pipeline.getPipe(i);
				String pipename=pipe.getName();
				if (pipe instanceof MessageSendingPipe) {
					MessageSendingPipe msp=(MessageSendingPipe)pipe;
					XmlBuilder pipeElem = new XmlBuilder("pipe");
					pipeElem.addAttribute("name",pipename);
					ISender sender = msp.getSender();
					pipeElem.addAttribute("sender",ClassUtils.nameOf(sender));
					pipesElem.addSubElement(pipeElem);
					if (sender instanceof WebServiceSender) {
						WebServiceSender s = (WebServiceSender)sender;
						String certificate = s.getCertificate();
						if (StringUtils.isNotEmpty(certificate)) {
							XmlBuilder certElem = new XmlBuilder("certificate");
							certElem.addAttribute("name",certificate);
							String certificateAuthAlias = s.getCertificateAuthAlias();
							certElem.addAttribute("authAlias",certificateAuthAlias);
							URL certificateUrl = ClassUtils.getResourceURL(this, certificate);
							certElem.addAttribute("url",certificateUrl.toString());
							pipeElem.addSubElement(certElem);
							String certificatePassword = s.getCertificatePassword();
							CredentialFactory certificateCf = new CredentialFactory(certificateAuthAlias, null, certificatePassword);
							String keystoreType = s.getKeystoreType();
							addCertificateInfo(certElem, certificateUrl, certificateCf.getPassword(), keystoreType, "Certificate chain");
						}
					} else {
						if (sender instanceof HttpSender) {
							HttpSender s = (HttpSender)sender;
							String certificate = s.getCertificate();
							if (StringUtils.isNotEmpty(certificate)) {
								XmlBuilder certElem = new XmlBuilder("certificate");
								certElem.addAttribute("name",certificate);
								String certificateAuthAlias = s.getCertificateAuthAlias();
								certElem.addAttribute("authAlias",certificateAuthAlias);
								URL certificateUrl = ClassUtils.getResourceURL(this, certificate);
								certElem.addAttribute("url",certificateUrl.toString());
								pipeElem.addSubElement(certElem);
								String certificatePassword = s.getCertificatePassword();
								CredentialFactory certificateCf = new CredentialFactory(certificateAuthAlias, null, certificatePassword);
								String keystoreType = s.getKeystoreType();
								addCertificateInfo(certElem, certificateUrl, certificateCf.getPassword(), keystoreType, "Certificate chain");
							}
						} else {
							if (sender instanceof FtpSender) {
								FtpSender s = (FtpSender)sender;
								String certificate = s.getCertificate();
								if (StringUtils.isNotEmpty(certificate)) {
									XmlBuilder certElem = new XmlBuilder("certificate");
									certElem.addAttribute("name",certificate);
									String certificateAuthAlias = s.getCertificateAuthAlias();
									certElem.addAttribute("authAlias",certificateAuthAlias);
									URL certificateUrl = ClassUtils.getResourceURL(this, certificate);
									certElem.addAttribute("url",certificateUrl.toString());
									pipeElem.addSubElement(certElem);
									String certificatePassword = s.getCertificatePassword();
									CredentialFactory certificateCf = new CredentialFactory(certificateAuthAlias, null, certificatePassword);
									String keystoreType = s.getCertificateType();
									addCertificateInfo(certElem, certificateUrl, certificateCf.getPassword(), keystoreType, "Certificate chain");
								}
							}
						}
					} 
				}
			}
		}
		request.setAttribute("usedCert", adapters.toXML());

		// Forward control to the specified success URI
		log.debug("forward to success");
		return (mapping.findForward("success"));
	}
}