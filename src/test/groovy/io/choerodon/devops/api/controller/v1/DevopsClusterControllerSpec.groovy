package io.choerodon.devops.api.controller.v1

import com.alibaba.fastjson.JSONObject
import com.github.pagehelper.PageInfo
import io.choerodon.core.domain.Page
import io.choerodon.core.exception.CommonException
import io.choerodon.core.exception.ExceptionResponse
import io.choerodon.devops.DependencyInjectUtil
import io.choerodon.devops.IntegrationTestConfiguration
import io.choerodon.devops.api.vo.ClusterNodeInfoVO
import io.choerodon.devops.api.vo.DevopsClusterRepVO
import io.choerodon.devops.api.vo.DevopsClusterReqVO
import io.choerodon.devops.app.service.IamService
import io.choerodon.devops.app.service.impl.ClusterNodeInfoServiceImpl
import io.choerodon.devops.infra.dto.AppServiceInstanceDTO
import io.choerodon.devops.infra.dto.DevopsClusterDTO
import io.choerodon.devops.infra.dto.DevopsEnvPodDTO
import io.choerodon.devops.infra.dto.DevopsEnvironmentDTO
import io.choerodon.devops.infra.dto.iam.OrganizationDTO
import io.choerodon.devops.infra.dto.iam.ProjectDTO
import io.choerodon.devops.infra.feign.BaseServiceClient
import io.choerodon.devops.infra.handler.ClusterConnectionHandler
import io.choerodon.devops.infra.mapper.*
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.context.annotation.Import
import org.springframework.data.redis.core.ListOperations
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise
import spock.lang.Subject

import static org.mockito.Matchers.*
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT

/**
 * Created by n!Ck
 * Date: 2018/11/13
 * Time: 14:03
 * Description: 
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@Import(IntegrationTestConfiguration)
@Subject(DevopsClusterController)
@Stepwise
class DevopsClusterControllerSpec extends Specification {
    private static final String MAPPING = "/v1/organizations/{organization_id}/clusters"
    private static Long ID

    @Autowired
    private TestRestTemplate restTemplate

    @Autowired
    private IamService iamRepository
    @Autowired
    private DevopsClusterMapper devopsClusterMapper
    @Autowired
    private DevopsClusterProPermissionMapper devopsClusterProPermissionMapper

    @Autowired
    private DevopsEnvironmentMapper devopsEnvironmentMapper
    @Autowired
    private DevopsEnvPodMapper devopsEnvPodMapper
    @Autowired
    private AppServiceInstanceMapper applicationInstanceMapper

    @Autowired
    private ClusterNodeInfoServiceImpl clusterNodeInfoService

    @Autowired
    @Qualifier("mockClusterConnectionHandler")
    private ClusterConnectionHandler clusterConnectionHandler

    BaseServiceClient iamServiceClient = Mockito.mock(BaseServiceClient.class)
    StringRedisTemplate mockStringRedisTemplate = Mockito.mock(StringRedisTemplate)

    @Shared
    private DevopsClusterDTO devopsClusterDTO = new DevopsClusterDTO()
    @Shared
    private DevopsEnvironmentDTO devopsEnvironmentDTO = new DevopsEnvironmentDTO()
    @Shared
    private AppServiceInstanceDTO applicationInstanceDO = new AppServiceInstanceDTO()
    @Shared
    private DevopsEnvPodDTO devopsEnvPodDO = new DevopsEnvPodDTO()

    @Shared
    private boolean isToInit = true
    @Shared
    private boolean isToClean = false

    def setup() {
        if (!isToInit) {
            return
        }

        DependencyInjectUtil.setAttribute(iamRepository, "baseServiceClient", iamServiceClient)
        DependencyInjectUtil.setAttribute(clusterNodeInfoService, "stringRedisTemplate", mockStringRedisTemplate)

        ProjectDTO projectDO = new ProjectDTO()
        projectDO.setId(1L)
        projectDO.setCode("pro")
        projectDO.setOrganizationId(1L)
        ResponseEntity<ProjectDTO> responseEntity = new ResponseEntity<>(projectDO, HttpStatus.OK)
        Mockito.doReturn(responseEntity).when(iamServiceClient).queryIamProject(anyLong())

        OrganizationDTO organizationDO = new OrganizationDTO()
        organizationDO.setId(1L)
        organizationDO.setCode("org")
        ResponseEntity<OrganizationDTO> responseEntity1 = new ResponseEntity<>(organizationDO, HttpStatus.OK)
        Mockito.doReturn(responseEntity1).when(iamServiceClient).queryOrganizationById(anyLong())

        List<ProjectDTO> projectDOList = new ArrayList<>()
        projectDOList.add(projectDO)
        PageInfo<ProjectDTO> projectDOPage = new PageInfo(projectDOList)
        ResponseEntity<PageInfo<ProjectDTO>> projectDOPageResponseEntity = new ResponseEntity<>(projectDOPage, HttpStatus.OK)
        Mockito.when(iamServiceClient.queryProjectByOrgId(anyLong(), anyInt(), anyInt(), isNull(), any(String[].class))).thenReturn(projectDOPageResponseEntity)

        devopsClusterDTO.setCode("uat")
        devopsClusterDTO.setId(1000L)
        devopsClusterDTO.setName("uat")
        devopsClusterDTO.setOrganizationId(1L)
        devopsClusterMapper.insert(devopsClusterDTO)

        devopsEnvironmentDTO.setName("env")
        devopsEnvironmentDTO.setCode("env")
        devopsEnvironmentDTO.setClusterId(devopsClusterDTO.getId())
        devopsEnvironmentMapper.insert(devopsEnvironmentDTO)

        applicationInstanceDO.setAppId(1000L)
        applicationInstanceDO.setAppName("app")
        applicationInstanceDO.setEnvId(devopsEnvironmentDTO.getId())
        applicationInstanceMapper.insert(applicationInstanceDO)

        devopsEnvPodDO.setAppInstanceId(applicationInstanceDO.getId())
        devopsEnvPodDO.setNodeName("uat01")
        devopsEnvPodDO.setRestartCount(11L)
        devopsEnvPodDO.setName("pod01")
        devopsEnvPodMapper.insert(devopsEnvPodDO)

        ListOperations<String, String> mockListOperations = Mockito.mock(ListOperations)
        Mockito.when(mockStringRedisTemplate.opsForList()).thenReturn(mockListOperations)
        Mockito.when(mockListOperations.size(anyString())).thenReturn(1L)

        ClusterNodeInfoVO clusterNodeInfoDTO = new ClusterNodeInfoVO()
        clusterNodeInfoDTO.setNodeName("uat01")

        Mockito.when(mockListOperations.range(anyString(), anyLong(), anyLong())).thenReturn(Arrays.asList(JSONObject.toJSONString(clusterNodeInfoDTO)))
    }

    def cleanup() {
        if (!isToClean) {
            return
        }
        DependencyInjectUtil.restoreDefaultDependency(iamRepository, "baseServiceClient")
        DependencyInjectUtil.restoreDefaultDependency(clusterNodeInfoService, "stringRedisTemplate")

        // 删除cluster
        devopsClusterMapper.selectAll().forEach{ devopsClusterMapper.delete(it) }

        // 删除clusterProRel
        devopsClusterProPermissionMapper.selectAll().forEach{ devopsClusterProPermissionMapper.delete(it) }
        devopsEnvironmentMapper.selectAll().forEach{ devopsEnvironmentMapper.delete(it) }
        devopsEnvPodMapper.selectAll().forEach{ devopsEnvPodMapper.delete(it) }
        applicationInstanceMapper.selectAll().forEach{ applicationInstanceMapper.delete(it) }

    }

    def "Create"() {
        given: '初始化DTO'
        isToInit = false
        DevopsClusterReqVO devopsClusterReqDTO = new DevopsClusterReqVO()
        List<Long> projectIds = new ArrayList<>()
        projectIds.add(1L)
        devopsClusterReqDTO.setCode("cluster")
        devopsClusterReqDTO.setName("cluster")
        devopsClusterReqDTO.setProjects(projectIds)
        devopsClusterReqDTO.setSkipCheckProjectPermission(false)

        when: '组织下创建集群'
        def str = restTemplate.postForObject(MAPPING, devopsClusterReqDTO, String.class, 1L)

        then: '校验返回值'
        str != null
    }

    def "Update"() {
        given: '初始化DTO'
        def searchCondition = new DevopsClusterDTO()
        searchCondition.setCode("cluster")
        searchCondition.setName("cluster")
        ID = devopsClusterMapper.selectOne(searchCondition).getId()

        DevopsClusterReqVO devopsClusterReqDTO = new DevopsClusterReqVO()
        List<Long> projectIds = new ArrayList<>()
        projectIds.add(2L)
        devopsClusterReqDTO.setCode("cluster")
        devopsClusterReqDTO.setProjects(projectIds)
        devopsClusterReqDTO.setName("updateCluster")
        devopsClusterReqDTO.setSkipCheckProjectPermission(false)

        searchCondition.setName("updateCluster")

        when: '更新集群下的项目'
        restTemplate.put(MAPPING + "?clusterId=" + ID, devopsClusterReqDTO, 2L)

        then: '校验是否更新'
        devopsClusterMapper.selectOne(searchCondition).getName() == "updateCluster"
    }

    def "Query"() {
        when: '查询单个集群信息'
        def dto = restTemplate.getForObject(MAPPING + "/" + ID, DevopsClusterRepVO.class, 1L)

        then: '校验返回值'
        dto["name"] == "updateCluster"
    }

    def "CheckName"() {
        when: '校验集群名唯一性'
        def exception = restTemplate.getForEntity(MAPPING + "/check_name?name=uniqueName", ExceptionResponse.class, 1L)

        then: '名字不存在不抛出异常'
        exception.statusCode.is2xxSuccessful()
        notThrown(CommonException)
    }

    def "CheckCode"() {
        when: '校验集群编码唯一性'
        def exception = restTemplate.getForEntity(MAPPING + "/check_code?code=uniqueCode", ExceptionResponse.class, 1L)

        then: '编码不存在不抛出异常'
        exception.statusCode.is2xxSuccessful()
        notThrown(CommonException)
    }

    def "PageProjects"() {
        given: '模糊查询参数'
        String[] str = new String[1]
        str[0] = "{}"

        when: '分页查询项目列表'
        def e = restTemplate.postForEntity(MAPPING + "/page_projects?page=0&size=10&cluster_id=" + ID, str, Page.class, 1L)

        then: '校验返回值'
        e.getBody().get(0)["code"] == "pro"
    }

    def "ListClusterProjects"() {
        when: '查询已有权限的项目列表'
        def e = restTemplate.getForEntity(MAPPING + "/list_cluster_projects/{clusterId}", List.class, 1L, ID)

        then: '校验返回值'
        e.getBody().get(0)["code"] == "pro"
    }

    def "QueryShell"() {
        given: 'mock envUtil'
        List<Long> envList = new ArrayList<>()
        envList.add(1L)
        envList.add(2L)
        clusterConnectionHandler.getConnectedEnvList() >> envList
        clusterConnectionHandler.getUpdatedEnvList() >> envList

        when: '查询shell脚本'
        def e = restTemplate.getForEntity(MAPPING + "/query_shell/{clusterId}", String.class, 1L, ID)

        then: '校验返回值'
        e.getBody() != null
    }

    def "ListCluster"() {
        given: '查询参数'
        String str = new String("{}")

        and: 'mock envUtil'
        List<Long> envList = new ArrayList<>()
        envList.add(1L)
        envList.add(2L)
        clusterConnectionHandler.getConnectedEnvList() >> envList
        clusterConnectionHandler.getUpdatedEnvList() >> envList

        when: '集群列表查询'
        def e = restTemplate.postForEntity(MAPPING + "/page_cluster?page=0&size=10&doPage=true", str, Page.class, 1L)

        then: '校验返回值'
        e.getBody().get(0)["code"] == "cluster"
    }

    def "get pods in node"() {
        given: '准备查询数据'

        when: '发送请求'
        def e = restTemplate.postForEntity(MAPPING + "/page_node_pods?page=0&size=10&cluster_id={clusterId}&node_name={nodeName}", "{}", Page.class, 1L, devopsClusterDTO.getId(), devopsEnvPodDO.getNodeName())

        then: '校验结果'
        e.getStatusCode().is2xxSuccessful()
        e.getBody().getContent().size() == 1
    }

    // 分页查询集群下的节点
    def "list cluster nodes"() {
        given: "准备数据"
        def url = MAPPING + "/page_nodes?cluster_id={cluster_id}&page=0&size=10"

        when: "发送请求"
        def res = restTemplate.getForEntity(url, Page, devopsClusterDTO.getOrganizationId(), devopsClusterDTO.getId())

        then: "校验结果"
        res.getStatusCode().is2xxSuccessful()
        res.getBody().getContent().size() == 1
    }

    // 根据集群id和节点名查询节点状态信息
    def "get certain node information"() {
        given: "准备数据"
        def url = MAPPING + "/nodes?cluster_id={clusterId}&node_name={nodeName}"

        when: "发送请求"
        def res = restTemplate.getForEntity(url, ClusterNodeInfoVO, devopsClusterDTO.getOrganizationId(), devopsClusterDTO.getId(), devopsEnvPodDO.getNodeName())

        then: "校验结果"
        res.getStatusCode().is2xxSuccessful()
        res.getBody().getNodeName() == devopsEnvPodDO.getNodeName()
    }

    def "DeleteCluster"() {
        given: 'mock envUtil'
        List<Long> envList = new ArrayList<>()
        envList.add(999L)
        clusterConnectionHandler.getConnectedEnvList() >> envList

        when: '删除集群'
        restTemplate.delete(MAPPING + "/{clusterId}", 1L, devopsClusterMapper.selectByPrimaryKey(ID).getId())

        then: '校验返回值'
        devopsClusterMapper.selectByPrimaryKey(ID) == null
    }

    def clean() {
        given:
        isToClean = true
    }
}
