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
package io.seata.server.cluster.raft.execute.branch;

import io.seata.server.cluster.raft.execute.AbstractRaftMsgExecute;
import io.seata.server.cluster.raft.sync.msg.RaftBaseMsg;
import io.seata.server.cluster.raft.sync.msg.RaftBranchSessionSyncMsg;
import io.seata.server.cluster.raft.sync.msg.dto.BranchTransactionDTO;
import io.seata.server.session.BranchSession;
import io.seata.server.session.GlobalSession;
import io.seata.server.session.SessionHolder;
import io.seata.server.storage.SessionConverter;
import io.seata.server.storage.raft.session.RaftSessionManager;

/**
 * @author jianbin.chen
 */
public class AddBranchSessionExecute extends AbstractRaftMsgExecute {

    @Override
    public Boolean execute(RaftBaseMsg syncMsg) throws Throwable {
        RaftBranchSessionSyncMsg sessionSyncMsg = (RaftBranchSessionSyncMsg)syncMsg;
        RaftSessionManager raftSessionManager = (RaftSessionManager) SessionHolder.getRootSessionManager(sessionSyncMsg.getGroup());
        BranchTransactionDTO branchTransactionDTO = sessionSyncMsg.getBranchSession();
        GlobalSession globalSession = raftSessionManager.findGlobalSession(branchTransactionDTO.getXid());
        BranchSession branchSession = SessionConverter.convertBranchSession(branchTransactionDTO);
        branchSession.lock();
        globalSession.add(branchSession);
        if (logger.isDebugEnabled()) {
            logger.debug("addBranch xid: {},branchId: {}", branchTransactionDTO.getXid(),
                branchTransactionDTO.getBranchId());
        }
        return true;
    }

}
