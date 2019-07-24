package io.choerodon.devops.infra.handler;

import java.util.List;

import io.choerodon.core.exception.CommonException;
import io.choerodon.devops.app.service.DevopsClusterProPermissionService;
import io.choerodon.devops.app.service.DevopsClusterService;
import io.choerodon.devops.app.service.DevopsEnvironmentService;
import io.choerodon.devops.app.service.AgentCommandService;
import io.choerodon.devops.infra.dto.DevopsClusterDTO;
import io.choerodon.websocket.session.AgentConfigurer;
import io.choerodon.websocket.session.AgentSessionManager;
import io.choerodon.websocket.session.Session;
import io.choerodon.websocket.session.SessionListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AgentInitHandler implements AgentConfigurer {

    @Autowired
    DevopsEnvironmentService devopsEnvironmentService;
    @Autowired
    DevopsClusterService devopsClusterService;
    @Autowired
    DevopsClusterProPermissionService devopsClusterProPermissionService;
    @Autowired
    private AgentCommandService agentCommandService;
    @Autowired
    private ClusterConnectionHandler clusterConnectionHandler;
    @Value("${services.gitlab.sshUrl}")
    private String gitlabSshUrl;

    @Override
    public void registerSessionListener(AgentSessionManager agentSessionManager) {
        AgentInitListener agentInitListener = new AgentInitListener();
        agentSessionManager.addListener(agentInitListener);
    }

    class AgentInitListener implements SessionListener {
        @Override
        public void onConnected(Session session) {
            try {
                Long clusterId = Long.valueOf(session.getRegisterKey().split(":")[1]);
                List<Long> connected = clusterConnectionHandler.getConnectedEnvList();
                List<Long> upgraded = clusterConnectionHandler.getUpdatedEnvList();
                if (connected.contains(clusterId) && !upgraded.contains(clusterId)) {
                    DevopsClusterDTO devopsClusterDTO = devopsClusterService.baseQuery(clusterId);
                    agentCommandService.upgradeCluster(devopsClusterDTO);
                }
                agentCommandService.initCluster(clusterId);
            } catch (Exception e) {
                throw new CommonException("read envId from agent session failed", e);
            }
        }

        @Override
        public Session onClose(String s) {
            return null;
        }
    }

}