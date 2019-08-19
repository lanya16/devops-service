package io.choerodon.devops.app.service.impl;

import java.io.File;
import java.util.List;

import io.choerodon.devops.api.vo.UserAttrVO;
import io.choerodon.devops.app.eventhandler.payload.ApplicationEventPayload;
import io.choerodon.devops.app.eventhandler.payload.DevOpsAppServicePayload;
import io.choerodon.devops.app.service.AppServiceService;
import io.choerodon.devops.app.service.ApplicationService;
import io.choerodon.devops.app.service.GitlabGroupService;
import io.choerodon.devops.app.service.UserAttrService;
import io.choerodon.devops.infra.dto.AppServiceDTO;
import io.choerodon.devops.infra.dto.DevopsProjectDTO;
import io.choerodon.devops.infra.dto.gitlab.GitlabProjectDTO;
import io.choerodon.devops.infra.feign.operator.GitlabServiceClientOperator;
import io.choerodon.devops.infra.mapper.AppServiceMapper;
import io.choerodon.devops.infra.mapper.DevopsConfigMapper;
import io.choerodon.devops.infra.mapper.DevopsProjectMapper;
import io.choerodon.devops.infra.util.FileUtil;
import io.choerodon.devops.infra.util.GitUserNameUtil;
import io.choerodon.devops.infra.util.GitUtil;
import io.choerodon.devops.infra.util.TypeUtil;
import io.choerodon.websocket.tool.UUIDTool;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @author zmf
 */
@Service
public class ApplicationServiceImpl implements ApplicationService {
    private final Logger logger = LoggerFactory.getLogger(ApplicationServiceImpl.class);

    @Autowired
    private GitlabGroupService gitlabGroupService;
    @Autowired
    private AppServiceService appServiceService;
    @Autowired
    private GitUtil gitUtil;
    @Autowired
    private UserAttrService userAttrService;
    @Autowired
    private GitlabServiceClientOperator gitlabServiceClientOperator;
    @Autowired
    private DevopsConfigMapper devopsConfigMapper;
    @Autowired
    private DevopsProjectMapper devopsProjectMapper;
    @Autowired
    private AppServiceMapper appServiceMapper;

    @Override
    public void handleApplicationCreation(ApplicationEventPayload payload) {
        logger.info("Handle creation of application, msg: {}", payload);

        // 创建gitlab应用组
        gitlabGroupService.createApplicationGroup(payload);

        // TODO 此处目前只支持选择项目层的应用作为模板，后续等待saga数据以支持平台层的应用作为模板
        if (payload.getSourceId() != null) {
            copyProjectAppServices(payload.getSourceId(), payload.getId(), payload.getProjectId());
        }
    }

    /**
     * 将原有应用的服务拷贝到新的服务下
     *
     * @param originAppId 原服务ID（模板应用ID）
     * @param newAppId    新建应用ID
     * @param projectId   项目id
     */
    private void copyProjectAppServices(Long originAppId, Long newAppId, Long projectId) {
        // 查询原应用下的所有服务
        AppServiceDTO search = new AppServiceDTO();
        search.setAppId(originAppId);
        List<AppServiceDTO> originalAppServices = appServiceMapper.select(search);

        // 复制所有服务
        originalAppServices.forEach(service -> {
            final String workingDir = gitUtil.getWorkingDirectory("application-service-copy-" + UUIDTool.genUuid());
            try {
                copyAppService(newAppId, service, projectId, workingDir);
            } catch (Exception e) {
                FileUtil.deleteDirectory(new File(workingDir));
                logger.warn("Failed to create application service from original application service with code '{}' and id {}", service.getCode(), service.getId());
            }
        });
    }

    /**
     * 拷贝一个服务到新的应用下，会发送saga消息
     *
     * @param newAppId           新的应用id
     * @param originalAppService 原有的应用下某个服务的信息
     * @param projectId          项目ID
     * @param workingDir         本地仓库的目录
     */
    private void copyAppService(final Long newAppId, final AppServiceDTO originalAppService, final Long projectId, String workingDir) {
        final AppServiceDTO newAppService = createDatabaseRecord(newAppId, originalAppService);
        final UserAttrVO userAttrVO = userAttrService.queryByUserId(TypeUtil.objToLong(GitUserNameUtil.getUserId()));
        DevopsProjectDTO devopsProjectDTO = devopsProjectMapper.selectByPrimaryKey(newAppId);

        //创建saga payload
        final DevOpsAppServicePayload payload = createPayload(newAppService, TypeUtil.objToInteger(userAttrVO.getGitlabUserId()), TypeUtil.objToInteger(devopsProjectDTO.getDevopsAppGroupId()));

        // 创建服务对应的代码仓库
        try {
            appServiceService.operationApplication(payload);
        } catch (Exception e) {
            AppServiceDTO appServiceDTO = appServiceService.baseQuery(payload.getAppServiceId());
            appServiceDTO.setFailed(true);
            appServiceService.baseUpdate(appServiceDTO);
            logger.warn("Failed to create application service with id {}", appServiceDTO.getId());
            return;
        }

        // 创建拉取原有代码仓库代码的token
        final String originalAccessToken = gitlabServiceClientOperator.createProjectToken(originalAppService.getGitlabProjectId(), TypeUtil.objToInteger(userAttrVO.getGitlabUserId()));

        // 创建token失败
        if (originalAccessToken == null) {
            logger.warn("Failed to create access token for gitlab repository of the original repository for the new application service with id: {}", newAppService.getId());
            return;
        }

        // 将原先服务的MASTER分支最新代码克隆到本地
        final File workingDirFile = new File(workingDir);
        GitlabProjectDTO originalGitlabProjectDTO = gitlabServiceClientOperator.queryProjectById(originalAppService.getGitlabProjectId());
        gitUtil.cloneRepository(workingDir, originalGitlabProjectDTO.getHttpUrlToRepo(), originalAccessToken, "master");

        // 删除所有commits
        File gitDir = new File(workingDir, ".git");
        if (gitDir.exists() && gitDir.isDirectory()) {
            FileUtil.deleteDirectory(gitDir);
        }

        // 本独初始化提交
        Git git;
        try {
            git = Git.init().setGitDir(workingDirFile).call();
            git.add().addFilepattern(".").call();
            git.commit().setMessage("[ADD] initial commit").call();
        } catch (GitAPIException e) {
            logger.warn("Failed to operate local git repository for application service with id: {}", newAppService.getId());
            FileUtil.deleteDirectory(workingDirFile);
            return;
        }

        final AppServiceDTO appServiceDTO = appServiceService.baseQuery(payload.getAppServiceId());
        final GitlabProjectDTO newGitProject = gitlabServiceClientOperator.queryProjectById(originalAppService.getGitlabProjectId());

        // 创建推送代码token
        final String newAccessToken = gitlabServiceClientOperator.createProjectToken(appServiceDTO.getGitlabProjectId(), TypeUtil.objToInteger(userAttrVO.getGitlabUserId()));

        // 创建token失败
        if (newAccessToken == null) {
            logger.warn("Failed to create access token for gitlab repository of the new application service with id: {}", appServiceDTO.getId());
            FileUtil.deleteDirectory(workingDirFile);
            return;
        }

        try {
            git.push().setRemote(newGitProject.getHttpUrlToRepo())
                    .setPushAll()
                    .setCredentialsProvider(new UsernamePasswordCredentialsProvider("", newAccessToken))
                    .call();
        } catch (GitAPIException e) {
            logger.warn("Failed to push local git repository to the remote repository of application service with id: {}", newAppService.getId());
            FileUtil.deleteDirectory(workingDirFile);
            return;
        }

        // 清理本地目录
        FileUtil.deleteDirectory(workingDirFile);

        // 发送消息通知
        appServiceService.sendCreateAppServiceInfo(newAppService, projectId);
    }

    /**
     * 根据应用服务信息创建payload
     *
     * @param newAppService 应用服务信息
     * @param gitlabUserId  gitlab用户id
     * @param groupId       组id
     * @return payload
     */
    private DevOpsAppServicePayload createPayload(AppServiceDTO newAppService, Integer gitlabUserId, Integer groupId) {
        DevOpsAppServicePayload devOpsAppServicePayload = new DevOpsAppServicePayload();
        devOpsAppServicePayload.setPath(newAppService.getCode());
        devOpsAppServicePayload.setUserId(gitlabUserId);
        devOpsAppServicePayload.setGroupId(groupId);
        devOpsAppServicePayload.setSkipCheckPermission(newAppService.getIsSkipCheckPermission());
        devOpsAppServicePayload.setAppServiceId(newAppService.getId());
        devOpsAppServicePayload.setIamProjectId(newAppService.getAppId());
        return devOpsAppServicePayload;
    }

    /**
     * 在数据库中新增一条记录
     *
     * @param newAppId           应用id
     * @param originalAppService 原来的服务id
     * @return 新纪录的id
     */
    private AppServiceDTO createDatabaseRecord(Long newAppId, AppServiceDTO originalAppService) {
        // 在新的APP中生成一条对应的服务记录
        AppServiceDTO appServiceDTO = new AppServiceDTO();
        appServiceDTO.setCode(originalAppService.getCode());
        appServiceDTO.setAppId(newAppId);

        // 便于saga重试
        AppServiceDTO record;
        if ((record = appServiceMapper.selectOne(appServiceDTO)) != null) {
            return record;
        }

        appServiceDTO.setName(originalAppService.getName());
        appServiceDTO.setDescription(originalAppService.getDescription());
        appServiceDTO.setType(originalAppService.getType());
        appServiceDTO.setActive(true);
        appServiceDTO.setSynchro(false);
        appServiceDTO.setIsSkipCheckPermission(true);
        appServiceDTO.setHarborConfigId(devopsConfigMapper.queryDefaultConfig("harbor").getId());
        appServiceDTO.setChartConfigId(devopsConfigMapper.queryDefaultConfig("chart").getId());

        return appServiceService.baseCreate(appServiceDTO);
    }
}