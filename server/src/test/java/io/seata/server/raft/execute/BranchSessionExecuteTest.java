/*
 *  Copyright 1999-2019 Seata.io Group.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.seata.server.raft.execute;

import io.seata.common.util.NetUtil;
import io.seata.config.ConfigurationCache;
import io.seata.core.exception.TransactionException;
import io.seata.core.model.BranchStatus;
import io.seata.core.model.BranchType;
import io.seata.server.cluster.raft.execute.branch.AddBranchSessionExecute;
import io.seata.server.cluster.raft.execute.branch.RemoveBranchSessionExecute;
import io.seata.server.cluster.raft.execute.branch.UpdateBranchSessionExecute;
import io.seata.server.cluster.raft.sync.msg.RaftBranchSessionSyncMsg;
import io.seata.server.cluster.raft.sync.msg.dto.BranchTransactionDTO;
import io.seata.server.lock.LockerManagerFactory;
import io.seata.server.session.BranchSession;
import io.seata.server.session.GlobalSession;
import io.seata.server.session.SessionHolder;
import io.seata.server.storage.SessionConverter;
import io.seata.server.store.StoreConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static io.seata.common.DefaultValues.DEFAULT_TX_GROUP;


/**
 * @author ptyin
 */
@SpringBootTest
class BranchSessionExecuteTest {

    private static GlobalSession GLOBAL_SESSION;

    private static final String XID = "123:123";

    private static final long BRANCH_ID = 0L;

    @BeforeAll
    public static void setUp(ApplicationContext context) throws TransactionException {
        System.setProperty("server.raft.serverAddr", NetUtil.getLocalIp() + ":9091");
        SessionHolder.init(StoreConfig.SessionMode.RAFT);
        LockerManagerFactory.destroy();
        LockerManagerFactory.init(StoreConfig.LockMode.RAFT);
    }

    @AfterAll
    public static void destroy() throws TransactionException {
        // Clear configuration
        ConfigurationCache.clear();
        System.clearProperty("server.raft.serverAddr");

        // Destroy SessionHolder and LockerManagerFactory
        SessionHolder.destroy();
        SessionHolder.init(null);
        LockerManagerFactory.destroy();
    }

    @BeforeEach
    public void addGlobalSession() throws TransactionException {
        GLOBAL_SESSION = mockGlobalSession();
        SessionHolder.getRootSessionManager().addGlobalSession(GLOBAL_SESSION);
    }

    @AfterEach
    public void removeTestSession() throws TransactionException {
        SessionHolder.getRootSessionManager().removeGlobalSession(GLOBAL_SESSION);
    }

    @Test
    public void testAdd() throws Throwable {
        BranchSession expected = mockBranchSession();

        AddBranchSessionExecute execute = new AddBranchSessionExecute();
        boolean success = execute.execute(convertToBranchSessionMsg(expected));
        Assertions.assertTrue(success);

        BranchSession branchSession = GLOBAL_SESSION.getBranch(BRANCH_ID);
        assertBranchSessionValid(expected, branchSession);
    }

    @Test
    public void testRemove() throws Throwable {
        GLOBAL_SESSION.add(mockBranchSession());

        BranchSession branchSession = GLOBAL_SESSION.getBranch(BRANCH_ID);
        Assertions.assertNotNull(branchSession);

        RemoveBranchSessionExecute execute = new RemoveBranchSessionExecute();
        boolean success = execute.execute(convertToBranchSessionMsg(branchSession));
        Assertions.assertTrue(success);

        branchSession = GLOBAL_SESSION.getBranch(BRANCH_ID);
        Assertions.assertNull(branchSession);
    }

    @Test
    public void testUpdate() throws Throwable {
        GLOBAL_SESSION.add(mockBranchSession());

        BranchSession branchSession = GLOBAL_SESSION.getBranch(BRANCH_ID);
        Assertions.assertNotNull(branchSession);

        BranchSession expected = mockBranchSession();
        expected.setStatus(BranchStatus.PhaseTwo_Committed);
        UpdateBranchSessionExecute execute = new UpdateBranchSessionExecute();
        boolean success = execute.execute(convertToBranchSessionMsg(expected));
        Assertions.assertTrue(success);

        branchSession = GLOBAL_SESSION.getBranch(BRANCH_ID);
        assertBranchSessionValid(expected, branchSession);
    }

    private static GlobalSession mockGlobalSession() {
        GlobalSession session = new GlobalSession("test", "test", "test", 5000);
        session.setXid(XID);
        session.setApplicationData("hello, world");
        session.setTransactionId(123);
        session.setBeginTime(System.currentTimeMillis());
        return session;
    }

    private static BranchSession mockBranchSession() {
        BranchSession session = new BranchSession();
        session.setXid(XID);
        session.setTransactionId(123);
        session.setBranchId(BRANCH_ID);
        session.setClientId("client");
        session.setResourceGroupId(DEFAULT_TX_GROUP);
        session.setResourceId("resource");
        session.setLockKey("test");
        session.setBranchType(BranchType.AT);
        session.setApplicationData("hello, world");
        return session;
    }

    private static void assertBranchSessionValid(BranchSession expected, BranchSession branchSession) {
        Assertions.assertNotNull(branchSession);
        Assertions.assertEquals(expected.getTransactionId(), branchSession.getTransactionId());
        Assertions.assertEquals(expected.getBranchId(), branchSession.getBranchId());
        Assertions.assertEquals(expected.getResourceId(), branchSession.getResourceId());
        Assertions.assertEquals(expected.getLockKey(), branchSession.getLockKey());
        Assertions.assertEquals(expected.getClientId(), branchSession.getClientId());
        Assertions.assertEquals(expected.getApplicationData(), branchSession.getApplicationData());
    }

    private static RaftBranchSessionSyncMsg convertToBranchSessionMsg(BranchSession branchSession) {
        RaftBranchSessionSyncMsg sessionMsg = new RaftBranchSessionSyncMsg();
        BranchTransactionDTO dto = SessionConverter.convertBranchTransactionDTO(branchSession);
        sessionMsg.setBranchSession(dto);
        return sessionMsg;
    }
}
