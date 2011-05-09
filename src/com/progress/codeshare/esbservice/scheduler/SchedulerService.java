/*
 * Copyright (C) 2009 - Progress Software
 */

package com.progress.codeshare.esbservice.scheduler;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.TimeZone;

import org.quartz.CronTrigger;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SchedulerFactory;
import org.quartz.SimpleTrigger;
import org.quartz.Trigger;
import org.quartz.impl.StdSchedulerFactory;

import com.sonicsw.xq.XQAddress;
import com.sonicsw.xq.XQConstants;
import com.sonicsw.xq.XQDispatch;
import com.sonicsw.xq.XQDispatchException;
import com.sonicsw.xq.XQEnvelope;
import com.sonicsw.xq.XQEnvelopeFactory;
import com.sonicsw.xq.XQInitContext;
import com.sonicsw.xq.XQLog;
import com.sonicsw.xq.XQMessage;
import com.sonicsw.xq.XQMessageException;
import com.sonicsw.xq.XQMessageFactory;
import com.sonicsw.xq.XQParameterInfo;
import com.sonicsw.xq.XQParameters;
import com.sonicsw.xq.XQPart;
import com.sonicsw.xq.XQServiceContext;
import com.sonicsw.xq.XQServiceEx;
import com.sonicsw.xq.XQServiceException;

public class SchedulerService implements XQServiceEx {

	// This is the XQLog (the container's logging mechanism).
	private XQLog m_xqLog = null;
	// This is the the log prefix that helps identify this service during
	// logging
	private String m_logPrefix = "";
	// These hold version information.
	private static int s_major = 2009; // year
	private static int s_minor = 915; // month-day
	private static int s_buildNumber = 1637; // hour-minute
	private final SchedulerFactory SCHEDULER_FACTORY = new StdSchedulerFactory();
	private static final String PARAM_NAME_END_TIME = "endTime";
	private static final String PARAM_NAME_REPEAT_COUNT = "repeatCount";
	private static final String PARAM_NAME_REPEAT_INTERVAL = "repeatInterval";
	private static final String PARAM_NAME_START_TIME = "startTime";
	private static final String PARAM_XML_DOCUMENT = "messageDocument";
	private static final String PARAM_ADDR_NAME = "callXQAddress";
	private static final String PARAM_NAME_TRIGGER_TYPE = "triggerType";
	private static final String TRIGGER_TYPE_CRON = "Cron";
	private static final String TRIGGER_TYPE_SIMPLE = "Simple";
	private static final String PARAM_NAME_CRON_EXPRESSION = "cronExpression";
	private static final String PARAM_NAME_TIMEZONE = "timeZone";
	private String msgContent = "";
	private XQAddress callAddress = null;
	private Scheduler scheduler;
	private JobDetail jobDetail;
	private final static String MSGFACTORY = "msgFactory";
	private final static String ENVFACTORY = "envFactory";
	private final static String ESBDISPATCHER = "esbDispatcher";
	private final static String XQLOG = "xqLog";
	private final static String MSGCONTENT = "msgContent";
	private final static String CALLADDRESS = "callAddress";
	private final static DateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm");
	
	// private XQInitContext ginitialContext;

	private final synchronized Date parseDate(String s) throws ParseException {
		return dateFormatter.parse(s);
	}

	/**
	 * ScheduleESB is the inner class that implements the Quartz's job interface
	 * This is where the real work is done (producing a message and dispatching
	 * it).
	 * 
	 * @author Renato Rissardi
	 * 
	 */
	public static class ScheduleESB implements Job {

		public ScheduleESB() {
		}

		private final synchronized String formatDate(Date d) {
			return dateFormatter.format(d);
		}

		public static ScheduleESB newInstance() {
			return new ScheduleESB();
		}

		public void execute(JobExecutionContext jobContext)
				throws JobExecutionException {

			if (jobContext == null) {
				throw new JobExecutionException("jobCotext is null. Can't execute.");
			}
			JobDataMap data = jobContext.getMergedJobDataMap();
			XQDispatch esbDispatcher = (XQDispatch) data.get(ESBDISPATCHER);
			XQEnvelopeFactory envFactory = (XQEnvelopeFactory) data.get(ENVFACTORY);
			XQMessageFactory msgFactory = (XQMessageFactory) data.get(MSGFACTORY);
			XQLog m_xqLog = (XQLog) data.get(XQLOG);
			XQAddress callAddress = (XQAddress) data.get(CALLADDRESS);
			String msgContent = (String) data.get(MSGCONTENT);
			if (esbDispatcher != null) {
				String instName = jobContext.getJobDetail().getName();
				String instGroup = jobContext.getJobDetail().getGroup();
				String instTrigger = jobContext.getTrigger().getName();

				try {
					m_xqLog.logInformation("JobDetail = " + jobContext.getPreviousFireTime());
					
					//Only sends messages when the scheduler is started
					if (jobContext.getPreviousFireTime() != null)
					{
						XQMessage msg = msgFactory.createMessage();
						msg.setStringHeader("Scheduler.Job.FireTime", formatDate(jobContext.getFireTime()));
						if (jobContext.getPreviousFireTime() != null) {
							msg.setStringHeader("Scheduler.Job.PreviousFireTime", formatDate(jobContext.getPreviousFireTime()));
						}
						
						if (jobContext.getNextFireTime() != null) {
							msg.setStringHeader("Scheduler.Job.NextFireTime", formatDate(jobContext.getNextFireTime()));
						}
						msg.setStringHeader("Scheduler.Job.FullName", jobContext.getJobDetail().getFullName());
						msg.setStringHeader("Scheduler.Job.Description", jobContext.getJobDetail().getDescription());
						msg.setStringHeader("Scheduler.Job.Name", instName);
						msg.setStringHeader("Scheduler.Job.Group", instGroup);
						msg.setStringHeader("Scheduler.Trigger.Name", instTrigger);
						msg.setStringHeader("Scheduler.Trigger.Description", jobContext.getTrigger().getDescription());
						msg.setStringHeader("Scheduler.Trigger.FullName", jobContext.getTrigger().getFullName());
						msg.setStringHeader("Scheduler.Trigger.Group", jobContext.getTrigger().getGroup());
						if (jobContext.getTrigger().getStartTime() != null) {
							msg.setStringHeader("Scheduler.Trigger.StartTime", formatDate(jobContext.getTrigger().getStartTime()));
						}
						if (jobContext.getTrigger().getEndTime() != null) {
							msg.setStringHeader("Scheduler.Trigger.EndTime", formatDate(jobContext.getTrigger().getEndTime()));
						}
						if (jobContext.getTrigger().getFinalFireTime() != null) {
							msg.setStringHeader("Scheduler.Trigger.FinalFireTime", formatDate(jobContext.getTrigger().getFinalFireTime()));
						}
						if (jobContext.getTrigger().getNextFireTime() != null) {
							msg.setStringHeader("Scheduler.Trigger.NextFireTime",formatDate(jobContext.getTrigger().getNextFireTime()));
						}
	
						m_xqLog.logDebug("msgContent: \n" + msgContent);
	
						XQPart prt = msg.createPart(msgContent, XQConstants.CONTENT_TYPE_XML);
	
						m_xqLog.logDebug("EndpointName : [" + callAddress.getName() + "]");
	
						msg.addPart(prt);
						XQEnvelope env = envFactory.createTargetedEnvelope( callAddress, msg);
						esbDispatcher.dispatch(env);
					}
					
				} catch (XQMessageException e) {
					m_xqLog.logError("Couldn't create message to dispatch. Error: " + e.getMessage());
					throw new JobExecutionException(e);
				} catch (XQDispatchException e) {
					m_xqLog.logError("Couldn't dispatch message. Error: " + e.getMessage());
					throw new JobExecutionException(e);
				} 

			} else {
				if (m_xqLog != null) {
					m_xqLog.logError("Dispatcher unavailable on ScheculeESB.execute.");
				} else {
					throw new JobExecutionException("Sonic ESB context not initialized inside ScheduleESB.execute!!");
				}
			}

		}
	}

	/**
	 * Constructor for a SchedulerService
	 */
	public SchedulerService() {
	}
		
	
	/**
	 * Initialize the XQService by processing its initialization parameters.
	 * 
	 * <p>
	 * This method implements a required XQService method.
	 * 
	 * @param initialContext
	 *            The Initial Service Context provides access to:<br>
	 *            <ul>
	 *            <li>The configuration parameters for this instance of the
	 *            SchedulerServiceType.</li>
	 *            <li>The XQLog for this instance of the SchedulerServiceType.</li>
	 *            </ul>
	 * @exception XQServiceException
	 *                Used in the event of some error.
	 */
	public void init(XQInitContext initialContext) throws XQServiceException {
		XQParameters params = initialContext.getParameters();
		m_xqLog = initialContext.getLog();
		setLogPrefix(params);
		m_xqLog.logInformation(m_logPrefix + " Initializing ...");

		writeStartupMessage(params);
		writeParameters(params);
		// perform initilization work.

		schedulerInitContext(initialContext);

		m_xqLog.logInformation(m_logPrefix + " Initialized ...");
	}

	private Trigger schedulerTriggerCron(String jobName, String triggerName,
			String triggerGroupName, String startTime, String endTime,
			String jobGroup, String cronExpression, String timeZone)
			throws XQServiceException {

		try {

			Trigger trigger = null;

			final CronTrigger cronTrigger = new CronTrigger();

			if (endTime != null) {
				cronTrigger.setEndTime(parseDate(endTime));
			}

			if (triggerGroupName != null) {
				cronTrigger.setGroup(triggerGroupName);
			}

			if (jobGroup != null) {
				cronTrigger.setJobGroup(jobGroup);
			}
			cronTrigger.setJobName(jobName);
			cronTrigger.setName(triggerName);

			cronTrigger.setStartTime(parseDate(startTime));

			cronTrigger.setCronExpression(cronExpression);

			if (timeZone != null) {
				cronTrigger.setTimeZone(TimeZone.getTimeZone(timeZone));
			}

			trigger = cronTrigger;

			return trigger;

		} catch (final Exception e) {
			throw new XQServiceException(e);
		}

	}

	private Trigger schedulerTriggerSimple(String triggerName,
			String triggerGroupName, long repeatInterval, String startTime,
			String endTime, int repeatCount) throws XQServiceException {

		try {

			Date dateEndTime = null;

			if (repeatCount == -1) {
				repeatCount = SimpleTrigger.REPEAT_INDEFINITELY;
			}

			repeatInterval = repeatInterval * 1000L;

			if (endTime != null) {
				dateEndTime = parseDate(endTime);
			}

			Date dateStartTime = parseDate(startTime);

			SimpleTrigger trigger = new SimpleTrigger(triggerName, triggerGroupName, dateStartTime, dateEndTime, repeatCount, repeatInterval);

			return trigger;

		} catch (final Exception e) {
			throw new XQServiceException(e);
		}

	}

	/**
	 * Performs initialization
	 */
	private void schedulerInitContext(final XQInitContext initCtx)
			throws XQServiceException {

		if (initCtx == null)
			throw new XQServiceException("Service Context cannot be null.");
		else {

			try {
				final XQParameters params = initCtx.getParameters();

				final String jobName = "job_" + params.getParameter(XQConstants.PARAM_SERVICE_NAME, XQConstants.PARAM_STRING);
				final String jobGroup = "jobGrp_" + params.getParameter(XQConstants.PARAM_SERVICE_NAME, XQConstants.PARAM_STRING);
				final String triggerName = "trg_" + params.getParameter(XQConstants.PARAM_SERVICE_NAME, XQConstants.PARAM_STRING);
				final String triggerGroupName = "trgGrp_" + params.getParameter( XQConstants.SERVICE_PARAM_SERVICE_TYPE, XQConstants.PARAM_STRING);
				long repeatInterval = params.getLongParameter(PARAM_NAME_REPEAT_INTERVAL, XQConstants.PARAM_STRING);
				final String startTime = params.getParameter(PARAM_NAME_START_TIME, XQConstants.PARAM_STRING);
				final String endTime = params.getParameter(PARAM_NAME_END_TIME, XQConstants.PARAM_STRING);
				final String triggerType = params.getParameter(PARAM_NAME_TRIGGER_TYPE, XQConstants.PARAM_STRING);
				final String cronExpression = params.getParameter(PARAM_NAME_CRON_EXPRESSION, XQConstants.PARAM_STRING);
				final String timeZone = params.getParameter(PARAM_NAME_TIMEZONE, XQConstants.PARAM_STRING);
				int repeatCount = params.getIntParameter( PARAM_NAME_REPEAT_COUNT, XQConstants.PARAM_STRING);
				msgContent = params.getParameter(PARAM_XML_DOCUMENT, XQConstants.PARAM_XML);
				String exitEndpointName = params.getParameter(PARAM_ADDR_NAME, XQConstants.PARAM_STRING);

				Trigger trigger = null;

				try {

					m_xqLog.logDebug("exitEndpointName : " + exitEndpointName);

					if (exitEndpointName != null) {
						callAddress = initCtx.getAddressFactory().createAddress(exitEndpointName, XQConstants.ADDRESS_ENDPOINT);
					} else {
						m_xqLog.logWarning(PARAM_ADDR_NAME + " is not an Endpoint!!! Trying to use default service exit endpoint.");
						exitEndpointName = params.getParameter(XQConstants.SERVICE_PARAM_EXIT_ENDPOINT_ADDRESS_LIST,XQConstants.PARAM_STRING);
						callAddress = initCtx.getAddressFactory().createAddress(exitEndpointName,XQConstants.ADDRESS_ENDPOINT);
					}
				} catch (Exception e) {
					m_xqLog.logError("Couldn't use exit endpoint " + exitEndpointName + ". " + e.getMessage());
					throw new XQServiceException(e);
				}

				jobDetail = new JobDetail(jobName, jobGroup, ScheduleESB.class);
				JobDataMap jobData = jobDetail.getJobDataMap();
				if (jobData == null) {
					jobData = new JobDataMap();
				}
				jobData.put(XQLOG, initCtx.getLog());
				jobData.put(ESBDISPATCHER, initCtx.getDispatcher());
				jobData.put(ENVFACTORY, initCtx.getEnvelopeFactory());
				jobData.put(CALLADDRESS, callAddress);
				jobData.put(MSGFACTORY, initCtx.getMessageFactory());
				jobData.put(MSGCONTENT, msgContent);
				jobDetail.setJobDataMap(jobData);

				scheduler = SCHEDULER_FACTORY.getScheduler();

				if (triggerType.equals(TRIGGER_TYPE_SIMPLE)) {
					m_xqLog.logDebug(" TRIGGER_TYPE : " + triggerType);
					trigger = schedulerTriggerSimple(triggerName,triggerGroupName, repeatInterval, startTime, endTime, repeatCount);
				}

				if (triggerType.equals(TRIGGER_TYPE_CRON)) {
					m_xqLog.logDebug(" TRIGGER_TYPE : " + triggerType);
					trigger = schedulerTriggerCron(jobName, triggerName, triggerGroupName, startTime, endTime, jobGroup, cronExpression, timeZone);
				}
				scheduler.startDelayed(10); // wait 10 seconds to start

				scheduler.scheduleJob(jobDetail, trigger);
				
				m_xqLog.logInformation("Scheduler Status step-tree :[" +  String.valueOf(scheduler.isStarted())  + "]");
				
			} catch (final SchedulerException e) {
				throw new XQServiceException(e);
			} catch (final Exception e) {
				throw new XQServiceException(e);
			}
		}
	}

	/**
	 * Handle the arrival of XQMessages in the INBOX.
	 * 
	 * <p>
	 * This method implement a required XQService method.
	 * 
	 * @param ctx
	 *            The service context.
	 * @exception XQServiceException
	 *                Thrown in the event of a processing error.
	 */
	public void service(XQServiceContext ctx) throws XQServiceException {
		m_xqLog.logInformation(m_logPrefix + "Service processing...");
		XQParameters params = ctx.getParameters();
		// Get the message.
		XQEnvelope env = null;
		
		//m_xqLog.logInformation(m_logPrefix
		//		+ "Waiting for scheduled jobs to finish...");
		
		while (ctx.hasNextIncoming()) {
			env = ctx.getNextIncoming();
			if (env != null) {
				XQMessage msg = env.getMessage();
				try {
					final String jobName = "job_" + params.getParameter(XQConstants.PARAM_SERVICE_NAME, XQConstants.PARAM_STRING);
					scheduler.triggerJob(jobName, Scheduler.DEFAULT_GROUP);
					msg.setBooleanHeader("Scheduler.isStarted", scheduler.isStarted());
					msg.setBooleanHeader("Scheduler.isInStandbyMode", scheduler.isInStandbyMode());
					msg.setStringHeader("Scheduler.SchedulerName", scheduler.getSchedulerName());
					// msg.setLongHeader("Scheduler.Job.RunTimes", ScheduleESB.getRunTimes());
				} catch (XQMessageException me) {
					throw new XQServiceException(
							"Exception accessing XQMessage: " + me.getMessage(), me);
				} catch (SchedulerException e) {
					throw new XQServiceException(e);
				}

				// Pass message onto the outbox.
				Iterator addressList = env.getAddresses();
				if (addressList.hasNext()) {
					// Add the message to the Outbox
					ctx.addOutgoing(env);
				}
			}
		}
		m_xqLog.logInformation(m_logPrefix + "Service processed...");
	}

	/**
	 * Clean up and get ready to destroy the service.
	 * 
	 * <p>
	 * This method implement a required XQService method.
	 */
	public void destroy() {
		m_xqLog.logInformation(m_logPrefix + "Destroying...");
		try {
			if (scheduler != null) {
				m_xqLog.logInformation(m_logPrefix + "Waiting for scheduled jobs to finish...");
				scheduler.shutdown(true); // //shutdown(true) does not return
				// until executing Jobs complete
				// execution
			}
		} catch (SchedulerException e) {
			m_xqLog.logError(m_logPrefix + e.getMessage());
			m_xqLog.logError(e);
		}
		m_xqLog.logInformation(m_logPrefix + "Destroyed...");
	}

	/**
	 * Called by the container on container start.
	 * 
	 * <p>
	 * This method implement a required XQServiceEx method.
	 */
	public void start() {
		m_xqLog.logInformation(m_logPrefix + "Starting...");
		/*
		 * removido porque a api n�o pode chamar o start apos a
		 * inicializa��o. try { if (scheduler != null) { if
		 * (scheduler.isStarted()) { m_xqLog.logInformation("Resuming
		 * scheduler..."); schedulerInitContext(ginitialContext);
		 * scheduler.resumeAll(); } else { m_xqLog.logInformation("Starting
		 * scheduler..."); scheduler.start(); } } } catch (SchedulerException e) {
		 * m_xqLog.logError(e); } catch (XQServiceException e) {
		 * m_xqLog.logError(e); }
		 */
		m_xqLog.logInformation(m_logPrefix + "Started...");
	}

	/**
	 * Called by the container on container stop.
	 * 
	 * <p>
	 * This method implement a required XQServiceEx method.
	 */
	public void stop() {
		m_xqLog.logInformation(m_logPrefix + "Stopping...");
		/*
		 * if (scheduler != null) { try { if (scheduler.isStarted()) {
		 * m_xqLog.logInformation("Pausing scheduler..."); scheduler.pauseAll(); } }
		 * catch (final SchedulerException e) { m_xqLog.logError(e); } }
		 */m_xqLog.logInformation(m_logPrefix + "Stopped...");
	}

	/**
	 * Clean up and get ready to destroy the service.
	 * 
	 */
	protected void setLogPrefix(XQParameters params) {
		String serviceName = params.getParameter(XQConstants.PARAM_SERVICE_NAME, XQConstants.PARAM_STRING);
		m_logPrefix = "[ " + serviceName + " ]";
	}

	/**
	 * Provide access to the service implemented version.
	 * 
	 */
	protected String getVersion() {
		return s_major + "." + s_minor + ". build " + s_buildNumber;
	}

	/**
	 * Writes a standard service startup message to the log.
	 */
	protected void writeStartupMessage(XQParameters params) {

		final StringBuffer buffer = new StringBuffer();

		String serviceTypeName = params.getParameter(XQConstants.SERVICE_PARAM_SERVICE_TYPE, XQConstants.PARAM_STRING);

		buffer.append("\n\n");
		buffer.append("\t\t " + serviceTypeName + "\n ");

		buffer.append("\t\t Version ");
		buffer.append(" " + getVersion());
		buffer.append("\n");

		buffer
				.append("\t\t Copyright (c) 2009, Progress Sonic Software Corporation (Brazil).");
		buffer.append("\n");

		buffer.append("\t\t All rights reserved. ");
		buffer.append("\n");

		m_xqLog.logInformation(buffer.toString());
	}

	/**
	 * Writes parameters to log.
	 */
	protected void writeParameters(XQParameters params) {

		final Map map = params.getAllInfo();
		final Iterator iter = map.values().iterator();

		while (iter.hasNext()) {
			final XQParameterInfo info = (XQParameterInfo) iter.next();

			if (info.getType() == XQConstants.PARAM_XML) {
				m_xqLog.logInformation(m_logPrefix + "Parameter Name =  " + info.getName());
			} else if (info.getType() == XQConstants.PARAM_STRING) {
				m_xqLog.logInformation(m_logPrefix + "Parameter Name = " + info.getName());
			}

			if (info.getRef() != null) {
				m_xqLog.logInformation(m_logPrefix + "Parameter Reference = " + info.getRef());

				// If this is too verbose
				// /then a simple change from logInformation to logDebug
				// will ensure file content is not displayed
				// unless the logging level is set to debug for the ESB
				// Container.
				m_xqLog.logInformation(m_logPrefix + "----Parameter Value Start--------");
				m_xqLog.logInformation("\n" + info.getValue() + "\n");
				m_xqLog.logInformation(m_logPrefix + "----Parameter Value End--------");
			} else {
				m_xqLog.logInformation(m_logPrefix + "Parameter Value = " + info.getValue());
			}
		}
	}
}