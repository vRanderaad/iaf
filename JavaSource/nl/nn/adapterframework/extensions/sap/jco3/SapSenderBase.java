/*
 * $Log: SapSenderBase.java,v $
 * Revision 1.1  2012-02-06 14:33:04  m00f069
 * Implemented JCo 3 based on the JCo 2 code. JCo2 code has been moved to another package, original package now contains classes to detect the JCo version available and use the corresponding implementation.
 *
 * Revision 1.5  2011/11/30 13:51:54  Peter Leeuwenburgh <peter.leeuwenburgh@ibissource.org>
 * adjusted/reversed "Upgraded from WebSphere v5.1 to WebSphere v6.1"
 *
 * Revision 1.1  2011/10/19 14:49:52  Peter Leeuwenburgh <peter.leeuwenburgh@ibissource.org>
 * Upgraded from WebSphere v5.1 to WebSphere v6.1
 *
 * Revision 1.3  2009/08/26 15:34:01  Gerrit van Brakel <gerrit.van.brakel@ibissource.org>
 * updated javadoc
 *
 * Revision 1.2  2008/01/30 14:41:58  Gerrit van Brakel <gerrit.van.brakel@ibissource.org>
 * modified javadoc
 *
 * Revision 1.1  2008/01/29 15:37:22  Gerrit van Brakel <gerrit.van.brakel@ibissource.org>
 * base class extracted from SapSender
 *
 */
package nl.nn.adapterframework.extensions.sap.jco3;

import nl.nn.adapterframework.configuration.ConfigurationException;
import nl.nn.adapterframework.core.ISenderWithParameters;
import nl.nn.adapterframework.core.PipeLineSession;
import nl.nn.adapterframework.core.SenderException;
import nl.nn.adapterframework.core.TimeOutException;
import nl.nn.adapterframework.extensions.sap.jco3.tx.DestinationFactoryUtils;
import nl.nn.adapterframework.parameters.Parameter;
import nl.nn.adapterframework.parameters.ParameterList;
import nl.nn.adapterframework.parameters.ParameterValueList;

import org.apache.commons.lang.StringUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.sap.conn.jco.JCoDestination;
import com.sap.conn.jco.JCoException;

/**
 * Base class for functions that call SAP.
 * <p><b>Configuration:</b>
 * <table border="1">
 * <tr><th>attributes</th><th>description</th><th>default</th></tr>
 * <tr><td>{@link #setName(String) name}</td><td>name of the Sender</td><td>&nbsp;</td></tr>
 * <tr><td>{@link #setSapSystemName(String) sapSystemName}</td><td>name of the {@link SapSystem} used by this object</td><td>&nbsp;</td></tr>
 * <tr><td>{@link #setSapSystemNameParam(String) sapSystemNameParam}</td><td>name of the parameter used to indicate the name of the {@link SapSystem} used by this object if the attribute <code>sapSystemName</code> is empty</td><td>sapSystemName</td></tr>
 * <tr><td>{@link #setLuwHandleSessionKey(String) luwHandleSessionKey}</td><td>session key in which LUW information is stored. When set, actions that share a LUW-handle will be executed using the same destination. Can only be used for synchronous functions</td><td>&nbsp;</td></tr>
 * <tr><td>{@link #setSynchronous(boolean) synchronous}</td><td>when <code>false</code>, the sender operates in RR mode: the a reply is expected from SAP, and the sender does not participate in a transaction. When <code>false</code>, the sender operates in FF mode: no reply is expected from SAP, and the sender joins the transaction, that must be present. The SAP transaction is committed right after the XA transaction is completed.</td><td>false</td></tr>
 * </table>
 * </p>
 * <table border="1">
 * <p><b>Parameters:</b>
 * <tr><th>name</th><th>type</th><th>remarks</th></tr>
 * <tr><td>sapSystemName</td><td>String</td><td>points to {@link SapSystem} to use; required when attribute <code>sapSystemName</code> is empty</td></tr>
 * </table>
 * </p>
 * 
 * @author  Gerrit van Brakel
 * @author  Jaco de Groot
 * @since   5.0
 * @version Id
 */
public abstract class SapSenderBase extends SapFunctionFacade implements ISenderWithParameters {

	private String luwHandleSessionKey;
	private String sapSystemNameParam="sapSystemName";
	private boolean synchronous=false;

	protected ParameterList paramList = null;
	
	
	public void configure() throws ConfigurationException {
		super.configure();
		if (paramList!=null) {
			paramList.configure();
		}
		if (StringUtils.isEmpty(getSapSystemName())) {
			if (StringUtils.isEmpty(getSapSystemNameParam())) {
				throw new ConfigurationException(getLogPrefix()+"if attribute sapSystemName is not specified, value of attribute sapSystemNameParam must indicate parameter to obtain name of sapSystem from");
			}
			if (paramList==null || paramList.findParameter(getSapSystemNameParam())==null) {
				throw new ConfigurationException(getLogPrefix()+"sapSystem must be specified, either in attribute sapSystemName, or via parameter ["+getSapSystemNameParam()+"]");
			}
		}
		if (!isSynchronous() && StringUtils.isNotEmpty(getLuwHandleSessionKey())) {
			throw new ConfigurationException(getLogPrefix()+"luwHandleSessionKey can only be used for synchronous calls to SAP");
		}
	}

	public void open() throws SenderException {
		try {
			openFacade();
		} catch (SapException e) {
			close();
			throw new SenderException(getLogPrefix()+"exception starting", e);
		}
	}
	
	public void close() {
		closeFacade();
	}

	public String sendMessage(String correlationID, String message) throws SenderException, TimeOutException {
		return sendMessage(correlationID,message,null);
	}

	public SapSystem getSystem(ParameterValueList pvl) throws SapException {
		if (StringUtils.isNotEmpty(getSapSystemName())) {
			return getSapSystem();
		}
		if (pvl==null) {
			throw new SapException("no parameters to determine sapSystemName from");
		}
		String SapSystemName=pvl.getParameterValue(getSapSystemNameParam()).asStringValue(null);
		if (StringUtils.isEmpty(SapSystemName)) {
			throw new SapException("could not determine sapSystemName using parameter ["+getSapSystemNameParam()+"]");
		}
		SapSystem result = getSapSystem(SapSystemName);
		if (log.isDebugEnabled()) log.debug(getLogPrefix()+"determined SapSystemName ["+SapSystemName+"]"); 
		if (result==null) {
			log.warn(getLogPrefix()+"could not find a SapSystem ["+SapSystemName+"] from Parameter ["+getSapSystemNameParam()+"]");
		}
		return getSapSystem(SapSystemName);
	}

	public JCoDestination getDestination(PipeLineSession session, SapSystem sapSystem) throws SenderException, SapException, JCoException {
		JCoDestination result;
		if (isSynchronous()) {
			if (StringUtils.isNotEmpty(getLuwHandleSessionKey())) {
				SapLUWHandle handle = SapLUWHandle.retrieveHandle(session, getLuwHandleSessionKey(), true, sapSystem, false);
				if (handle==null) {
					throw new SenderException("cannot find LUW handle from session key ["+getLuwHandleSessionKey()+"]");
				}
				result = handle.getDestination();
			} else {
				result = sapSystem.getDestination();
			}
		} else {
			result = DestinationFactoryUtils.getTransactionalDestination(sapSystem, true);
			if (result==null) {
				if (!TransactionSynchronizationManager.isSynchronizationActive()) {
					throw new SenderException("can only be called from within a transaction");
				}
				throw new SenderException(getLogPrefix()+"Could not obtain Jco Destination");
			}
		}
		return result;
	}

	public String getTid(JCoDestination destination, SapSystem sapSystem) throws SapException, JCoException {
		if (isSynchronous()) {
			return null;
		}
		return DestinationFactoryUtils.getTransactionalTid(sapSystem,destination,true);
	}

	public void addParameter(Parameter p) {
		if (paramList==null) {
			paramList=new ParameterList();
		}
		paramList.add(p);
	}



	public void setLuwHandleSessionKey(String string) {
		luwHandleSessionKey = string;
	}
	public String getLuwHandleSessionKey() {
		return luwHandleSessionKey;
	}

	public void setSapSystemNameParam(String string) {
		sapSystemNameParam = string;
	}
	public String getSapSystemNameParam() {
		return sapSystemNameParam;
	}

	protected void setSynchronous(boolean b) {
		synchronous = b;
	}
	public boolean isSynchronous() {
		return synchronous;
	}

}