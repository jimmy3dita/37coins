package com._37coins;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import javax.mail.internet.AddressException;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import com._37coins.activities.BitcoindActivities;
import com._37coins.activities.MessagingActivities;
import com._37coins.bcJsonRpc.pojo.Transaction;
import com._37coins.bizLogic.NonTxWorkflowImpl;
import com._37coins.workflow.NonTxWorkflowClient;
import com._37coins.workflow.NonTxWorkflowClientFactory;
import com._37coins.workflow.NonTxWorkflowClientFactoryImpl;
import com._37coins.workflow.pojo.DataSet;
import com._37coins.workflow.pojo.DataSet.Action;
import com._37coins.workflow.pojo.MessageAddress;
import com._37coins.workflow.pojo.MessageAddress.MsgType;
import com._37coins.workflow.pojo.PaymentAddress;
import com._37coins.workflow.pojo.PaymentAddress.PaymentType;
import com._37coins.workflow.pojo.Withdrawal;
import com.amazonaws.services.simpleworkflow.flow.annotations.Asynchronous;
import com.amazonaws.services.simpleworkflow.flow.core.Promise;
import com.amazonaws.services.simpleworkflow.flow.junit.AsyncAssert;
import com.amazonaws.services.simpleworkflow.flow.junit.FlowBlockJUnit4ClassRunner;
import com.amazonaws.services.simpleworkflow.flow.junit.WorkflowTest;

@RunWith(FlowBlockJUnit4ClassRunner.class)
public class NonTxWorkflowTest {

	@Rule
	public WorkflowTest workflowTest = new WorkflowTest();

	final List<DataSet> trace = new ArrayList<>();

	private NonTxWorkflowClientFactory workflowFactory = new NonTxWorkflowClientFactoryImpl();
	
	final List<Transaction> list = new ArrayList<>();

	@Before
    public void setUp() throws Exception {
        // Create and register mock activity implementation to be used during test run
        BitcoindActivities activities = new BitcoindActivities() {
			@Override
			public String sendTransaction(BigDecimal amount, BigDecimal fee, String fromCn,
					String toCn, String toAddress, String id, String comment) {
				return null;
			}
			@Override
			public BigDecimal getAccountBalance(String cn) {
				return new BigDecimal("2.5");
			}
			@Override
			public String getNewAddress(String cn) {
				if (cn.equalsIgnoreCase("1")){
					return "1Nsateouhasontuh234";
				}else{
					throw new RuntimeException("not found");
				}
			}
			@Override
			public Long getAccount(String bcAddress) {
				return null;
			}
			@Override
			public List<Transaction> getAccountTransactions(String cn) {
				list.add(new Transaction().setTime(System.currentTimeMillis()).setComment("hallo").setAmount(new BigDecimal("0.4")).setTo("hast@test.com"));
				return list;
			}
			@Override
			public BigDecimal getTransactionVolume(String cn, int hours) {
				return null;
			}
        };
        MessagingActivities mailActivities = new MessagingActivities() {

			@Override
			public void sendMessage(DataSet rsp) {
				trace.add(rsp);
			}
			@Override
			public Action sendConfirmation(DataSet rsp, String workflowId) {
				return null;
			}
			@Override
			public DataSet readMessageAddress(DataSet data) {
				return data.setTo(new MessageAddress()
					.setAddress("")
					.setAddressType(MsgType.SMS)
					.setGateway(""));
			}
			@Override
			public Action phoneConfirmation(DataSet rsp, String workflowId) {
				return null;
			}
			@Override
			public void putCache(DataSet rsp) {
			}
			@Override
			public void putAddressCache(DataSet rsp) {
			}

        };
        workflowTest.addActivitiesImplementation(activities);
        workflowTest.addActivitiesImplementation(mailActivities);
        workflowTest.addWorkflowImplementationType(NonTxWorkflowImpl.class);
    }

	@After
	public void tearDown() throws Exception {
		// trace = null;
	}

	@Test
	public void testCreateAccount() throws AddressException {
		NonTxWorkflowClient workflow = workflowFactory.getClient();
		DataSet data = new DataSet()
			.setAction(Action.SIGNUP)
			.setCn("1")
			.setTo(new MessageAddress()
				.setAddress("test@37coins.com"));
		Promise<Void> booked = workflow.executeCommand(data);
		data.setPayload(new PaymentAddress()
				.setAddress("1Nsateouhasontuh234")
				.setAddressType(PaymentType.BTC));
		validate("successfull create", data, trace,booked);
	}

	@Test
	public void testGetAddress() throws AddressException {
		NonTxWorkflowClient workflow = workflowFactory.getClient();
		DataSet data = new DataSet()
			.setAction(Action.DEPOSIT_REQ)
			.setCn("1")
			.setTo(new MessageAddress()
				.setAddress("test@37coins.com"));
		Promise<Void> booked = workflow.executeCommand(data);
		data
			.setPayload(new PaymentAddress()
				.setAddress("1Nsateouhasontuh234")
				.setAddressType(PaymentType.BTC));
		validate("address returned", data, trace,booked);
	}
	
	@Test
	public void testDepositAccount() throws AddressException {
		NonTxWorkflowClient workflow = workflowFactory.getClient();
		DataSet data = new DataSet()
			.setAction(Action.DEPOSIT_CONF)
			.setCn("1")
    		.setPayload(new Withdrawal()
    			.setMsgDest(new MessageAddress().setAddress("from@37coins.com"))
    			.setComment("hallo")
    			.setAmount(new BigDecimal("0.01")))
    		.setTo(new MessageAddress()
					.setAddress("")
					.setAddressType(MsgType.SMS)
					.setGateway(""));
		Promise<Void> booked = workflow.executeCommand(data);
		Withdrawal w = (Withdrawal)data.getPayload();
		w.setBalance(new BigDecimal("2.5"));
		validate("successfull deposit", data, trace,booked);
	}

	@Test
	public void testBalanceAccount() throws AddressException {
		NonTxWorkflowClient workflow = workflowFactory.getClient();
		DataSet data = new DataSet()
			.setAction(Action.BALANCE)
			.setCn("1");
		Promise<Void> booked = workflow.executeCommand(data);
		data.setPayload(new Withdrawal()
				.setBalance(new BigDecimal("2.5")));
		validate("successfull balance", data, trace, booked);
	}
	
	@Test
	public void testTransactions() throws AddressException {
		NonTxWorkflowClient workflow = workflowFactory.getClient();
		DataSet data = new DataSet()
			.setAction(Action.TRANSACTION)
			.setCn("1");
		Promise<Void> booked = workflow.executeCommand(data);
		data.setPayload(list);
		validate("successfull tx", data, trace, booked);
	}

	@Asynchronous
	public void validate(String desc, Object expected, List<DataSet> l,Promise<Void> booked){
		AsyncAssert.assertEqualsWaitFor(desc, expected, l.get(0), booked);
	}
	
}