package io.choerodon.devops.app.service.impl;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.google.gson.Gson;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import org.apache.commons.compress.utils.IOUtils;
import org.eclipse.jgit.api.Git;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;

import io.choerodon.base.domain.PageRequest;
import io.choerodon.core.exception.CommonException;
import io.choerodon.devops.api.validator.ApplicationValidator;
import io.choerodon.devops.api.validator.HarborMarketVOValidator;
import io.choerodon.devops.api.vo.*;
import io.choerodon.devops.app.eventhandler.payload.AppMarketDownloadPayload;
import io.choerodon.devops.app.eventhandler.payload.AppServiceDownloadPayload;
import io.choerodon.devops.app.eventhandler.payload.AppServiceVersionDownloadPayload;
import io.choerodon.devops.app.service.*;
import io.choerodon.devops.infra.config.ConfigurationProperties;
import io.choerodon.devops.infra.dto.*;
import io.choerodon.devops.infra.dto.gitlab.GitlabProjectDTO;
import io.choerodon.devops.infra.dto.gitlab.MemberDTO;
import io.choerodon.devops.infra.dto.harbor.User;
import io.choerodon.devops.infra.dto.iam.ApplicationDTO;
import io.choerodon.devops.infra.dto.iam.OrganizationDTO;
import io.choerodon.devops.infra.dto.iam.ProjectDTO;
import io.choerodon.devops.infra.enums.AccessLevel;
import io.choerodon.devops.infra.feign.MarketServiceClient;
import io.choerodon.devops.infra.feign.operator.BaseServiceClientOperator;
import io.choerodon.devops.infra.feign.operator.GitlabServiceClientOperator;
import io.choerodon.devops.infra.handler.RetrofitHandler;
import io.choerodon.devops.infra.mapper.AppServiceMapper;
import io.choerodon.devops.infra.thread.CommandWaitForThread;
import io.choerodon.devops.infra.util.*;

/**
 * Creator: ChangpingShi0213@gmail.com
 * Date:  10:28 2019/8/8
 * Description:
 */
@Component
public class OrgAppMarketServiceImpl implements OrgAppMarketService {
    public static final Logger LOGGER = LoggerFactory.getLogger(CommandWaitForThread.class);
    private static final Gson gson = new Gson();

    private static final String APPLICATION = "application";
    private static final String CHART = "chart";
    private static final String REPO = "repo";
    private static final String IMAGES = "images";
    private static final String PUSH_IAMGES = "push_image.sh";
    private static final String MARKET_PRO = "market-downloaded-app";
    private static final String DOWNLOADED_APP = "downloaded-app";
    private static final String HARBOR_NAME = "harbor_default";
    private static final String SITE_APP_GROUP_NAME_FORMAT = "site_%s";
    private static final String APP_OUT_FILE_FORMAT = "%s%s%s-%s-%s%s";
    private static final String APP_FILE_PATH_FORMAT = "%s%s%s%s%s";
    private static final String APP_TEMP_PATH_FORMAT = "%s%s%s";
    private static final String ZIP = ".zip";
    private static final String GIT = ".git";
    private static final String TGZ = ".tgz";
    private static final String VALUES = "values.yaml";
    private static final String DEPLOY_ONLY = "deploy_only";
    private static final String DOWNLOAD_ONLY = "download_only";
    private static final String ALL = "all";
    private static final String MARKET = "market";
    private static final String LINE = "line.separator";
    private static final String SHELL = "/shell";
    @Value("${services.helm.url}")
    private String helmUrl;
    @Value("${services.harbor.baseUrl}")
    private String harborUrl;
    @Value("${services.gitlab.url}")
    private String gitlabUrl;

    @Autowired
    private AppServiceMapper appServiceMapper;
    @Autowired
    private AppServiceVersionService appServiceVersionService;
    @Autowired
    private GitUtil gitUtil;
    @Autowired
    private ChartUtil chartUtil;
    @Autowired
    private UserAttrService userAttrService;
    @Autowired
    private AppServiceService appServiceService;
    @Autowired
    private HarborService harborService;
    @Autowired
    private DevopsConfigService devopsConfigService;
    @Autowired
    private BaseServiceClientOperator baseServiceClientOperator;
    @Autowired
    private GitlabServiceClientOperator gitlabServiceClientOperator;
    @Autowired
    private AppServiceVersionValueService appServiceVersionValueService;
    @Autowired
    private AppServiceVersionReadmeService appServiceVersionReadmeService;
    @Autowired
    private DevopsProjectService devopsProjectService;

    @Override
    public PageInfo<AppServiceUploadVO> pageByAppId(Long appId,
                                                    PageRequest pageRequest,
                                                    String params) {
        Map<String, Object> mapParams = TypeUtil.castMapParams(params);
        List<String> paramList = TypeUtil.cast(mapParams.get(TypeUtil.PARAMS));
        PageInfo<AppServiceDTO> appServiceDTOPageInfo = PageHelper.startPage(
                pageRequest.getPage(),
                pageRequest.getSize(),
                PageRequestUtil.getOrderBy(pageRequest)).doSelectPageInfo(() -> appServiceMapper.listByAppId(appId, TypeUtil.cast(mapParams.get(TypeUtil.SEARCH_PARAM)), paramList));

        PageInfo<AppServiceUploadVO> appServiceMarketVOPageInfo = ConvertUtils.convertPage(appServiceDTOPageInfo, this::dtoToMarketVO);
        List<AppServiceUploadVO> list = appServiceMarketVOPageInfo.getList();
        list.forEach(appServiceMarketVO -> appServiceMarketVO.setAppServiceVersionUploadVOS(
                ConvertUtils.convertList(appServiceVersionService.baseListByAppServiceId(appServiceMarketVO.getAppServiceId()), AppServiceVersionUploadVO.class)));
        appServiceMarketVOPageInfo.setList(list);
        return appServiceMarketVOPageInfo;
    }

    @Override
    public List<AppServiceUploadVO> listAllAppServices() {
        List<AppServiceDTO> appServiceDTOList = appServiceMapper.selectAll();
        return ConvertUtils.convertList(appServiceDTOList, this::dtoToMarketVO);
    }

    @Override
    public String createHarborRepository(HarborMarketVO harborMarketVO) {
        HarborMarketVOValidator.checkEmailAndPassword(harborMarketVO);
        return harborService.createHarborForAppMarket(harborMarketVO);
    }

    @Override
    public List<AppServiceVersionUploadVO> listServiceVersionsByAppServiceId(Long appServiceId) {
        List<AppServiceVersionDTO> appServiceVersionDTOList = appServiceVersionService.baseListByAppServiceId(appServiceId);
        return ConvertUtils.convertList(appServiceVersionDTOList, AppServiceVersionUploadVO.class);
    }

    @Override
    public void uploadAPP(AppMarketUploadVO marketUploadVO) {
        //创建根目录 应用
        String appFilePath = gitUtil.getWorkingDirectory(APPLICATION + System.currentTimeMillis());
        FileUtil.createDirectory(appFilePath);
        File appFile = new File(appFilePath);
        List<String> zipFileList = new ArrayList<>();
        Map<String, String> map = new HashMap<>();
        try {
            switch (marketUploadVO.getStatus()) {
                case DOWNLOAD_ONLY: {
                    String appRepoFilePath = String.format(APP_FILE_PATH_FORMAT, appFilePath, File.separator, REPO, File.separator, marketUploadVO.getAppCode());
                    //clone 并压缩源代码
                    marketUploadVO.getAppServiceUploadVOS().forEach(appServiceMarketVO -> packageRepo(appServiceMarketVO, appRepoFilePath, marketUploadVO.getIamUserId()));
                    String outputFilePath = String.format(APP_OUT_FILE_FORMAT, appFile.getParent(), File.separator, REPO, marketUploadVO.getAppCode(), System.currentTimeMillis(), ZIP);
                    toZip(outputFilePath, appRepoFilePath);
                    zipFileList.add(outputFilePath);
                    break;
                }
                case DEPLOY_ONLY: {
                    String appChartFilePath = String.format(APP_FILE_PATH_FORMAT, appFilePath, File.separator, CHART, File.separator, marketUploadVO.getAppCode());
                    marketUploadVO.getAppServiceUploadVOS().forEach(appServiceMarketVO -> packageChart(appServiceMarketVO, appChartFilePath));

                    String outputFilePath = String.format(APP_OUT_FILE_FORMAT, appFile.getParent(), File.separator, CHART, marketUploadVO.getAppCode(), System.currentTimeMillis(), ZIP);
                    toZip(outputFilePath, appChartFilePath);
                    map = pushImageForUpload(marketUploadVO);
                    break;
                }
                case ALL: {
                    String appRepoFilePath = String.format(APP_FILE_PATH_FORMAT, appFilePath, File.separator, REPO, File.separator, marketUploadVO.getAppCode());
                    String appChartFilePath = String.format(APP_FILE_PATH_FORMAT, appFilePath, File.separator, CHART, File.separator, marketUploadVO.getAppCode());

                    marketUploadVO.getAppServiceUploadVOS().forEach(appServiceMarketVO -> {
                        packageRepo(appServiceMarketVO, appRepoFilePath, marketUploadVO.getIamUserId());
                        packageChart(appServiceMarketVO, appChartFilePath);
                    });

                    String outputFilePath = String.format(APP_OUT_FILE_FORMAT, appFile.getParent(), File.separator, CHART, marketUploadVO.getAppCode(), System.currentTimeMillis(), ZIP);
                    toZip(outputFilePath, appChartFilePath);

                    outputFilePath = String.format(APP_OUT_FILE_FORMAT, appFile.getParent(), File.separator, REPO, marketUploadVO.getAppCode(), System.currentTimeMillis(), ZIP);
                    toZip(outputFilePath, appRepoFilePath);
                    map = pushImageForUpload(marketUploadVO);
                    break;
                }
                default:
                    throw new CommonException("error.status.publish");
            }
            fileUpload(zipFileList, marketUploadVO, map);
            zipFileList.forEach(FileUtil::deleteFile);
            FileUtil.deleteDirectory(appFile);
        } catch (CommonException e) {
            baseServiceClientOperator.publishFail(marketUploadVO.getProjectId(), marketUploadVO.getAppId(), e.getCode());
            throw new CommonException(e.getCode());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void downLoadApp(AppMarketDownloadPayload appMarketDownloadVO) {
        DevopsProjectDTO projectDTO = devopsProjectService.queryByAppId(appMarketDownloadVO.getAppId());
        UserAttrDTO userAttrDTO = userAttrService.baseQueryById(appMarketDownloadVO.getIamUserId());

        ApplicationDTO applicationDTO = baseServiceClientOperator.queryAppById(appMarketDownloadVO.getAppId());
        String groupPath = String.format(SITE_APP_GROUP_NAME_FORMAT, applicationDTO.getCode());
        appMarketDownloadVO.getAppServiceDownloadPayloads().forEach(downloadPayload -> {
            //1. 校验是否已经下载过
            AppServiceDTO appServiceDTO = appServiceService.baseQueryByCode(downloadPayload.getAppServiceCode(), appMarketDownloadVO.getAppId());
            downloadPayload.setAppId(appMarketDownloadVO.getAppId());
            Boolean isFirst = appServiceDTO == null;
            if (appServiceDTO == null) {
                appServiceDTO = createGitlabProject(downloadPayload, TypeUtil.objToInteger(projectDTO.getDevopsAppGroupId()), userAttrDTO.getGitlabUserId());
            }
            String applicationDir = APPLICATION + System.currentTimeMillis();
            String accessToken = appServiceService.getToken(appServiceDTO.getGitlabProjectId(), applicationDir, userAttrDTO);
            createAppServiceVersion(downloadPayload, appServiceDTO, groupPath, isFirst, accessToken);
        });
        if (appMarketDownloadVO.getUser() != null) {
            pushImageForDownload(appMarketDownloadVO);
        }
    }

    private AppServiceDTO createGitlabProject(AppServiceDownloadPayload downloadPayload, Integer gitlabGroupId, Long gitlabUserId) {
        ApplicationValidator.checkApplicationService(downloadPayload.getAppServiceCode());
        AppServiceDTO appServiceDTO = ConvertUtils.convertObject(downloadPayload, AppServiceDTO.class);
        appServiceDTO.setName(downloadPayload.getAppServiceName());
        appServiceDTO.setType(downloadPayload.getAppServiceType());
        appServiceDTO.setCode(downloadPayload.getAppServiceCode());
        appServiceDTO.setActive(true);
        appServiceDTO.setIsSkipCheckPermission(true);
        //2. 第一次下载创建应用服务
        //2. 分配所在gitlab group 用户权限
        MemberDTO memberDTO = gitlabServiceClientOperator.queryGroupMember(gitlabGroupId, TypeUtil.objToInteger(gitlabUserId));
        if (memberDTO == null || memberDTO.getId() == null || !memberDTO.getAccessLevel().equals(AccessLevel.OWNER.value)) {
            memberDTO = new MemberDTO();
            memberDTO.setId(TypeUtil.objToInteger(gitlabUserId));
            memberDTO.setAccessLevel(AccessLevel.OWNER.value);
            gitlabServiceClientOperator.createGroupMember(gitlabGroupId, memberDTO);
        }

        //3. 创建gitlab project
        GitlabProjectDTO gitlabProjectDTO = gitlabServiceClientOperator.createProject(gitlabGroupId,
                downloadPayload.getAppServiceCode(),
                TypeUtil.objToInteger(gitlabUserId),
                true);
        appServiceDTO.setGitlabProjectId(gitlabProjectDTO.getId());
        appServiceDTO.setSynchro(true);
        appServiceDTO.setFailed(false);
        appServiceService.baseCreate(appServiceDTO);
        return appServiceDTO;
    }

    private void createAppServiceVersion(AppServiceDownloadPayload downloadPayload, AppServiceDTO appServiceDTO, String groupPath, Boolean isFirst, String accessToken) {
        Long appServiceId = appServiceDTO.getId();
        downloadPayload.getAppServiceVersionDownloadPayloads().forEach(appServiceVersionPayload -> {
            AppServiceVersionDTO versionDTO = new AppServiceVersionDTO();
            Git git = null;
            String repository = null;
            if (appServiceVersionPayload.getChartFilePath() != null && !appServiceVersionPayload.getChartFilePath().isEmpty()) {
                String chartFilePath = String.format("%s%s", gitUtil.getWorkingDirectory(APPLICATION + System.currentTimeMillis()), TGZ);
                fileDownload(appServiceVersionPayload.getChartFilePath(), chartFilePath);
                chartResolver(appServiceVersionPayload, appServiceId, downloadPayload.getAppServiceCode(), new File(chartFilePath), versionDTO);
            }
            if (appServiceVersionPayload.getRepoFilePath() != null && !appServiceVersionPayload.getRepoFilePath().isEmpty()) {
                String repoFilePath = String.format("%s%s", gitUtil.getWorkingDirectory(APPLICATION + System.currentTimeMillis()), ZIP);
                fileDownload(appServiceVersionPayload.getRepoFilePath(), repoFilePath);
                gitResolver(appServiceVersionPayload, isFirst, groupPath, new File(repoFilePath), downloadPayload, accessToken, versionDTO, appServiceId);

            }
        });
    }

    /**
     * git 解析
     *
     * @param isFirst         是否第一次下载
     * @param groupPath       gitlab项目组名称
     * @param file            源码文件
     * @param downloadPayload
     * @param accessToken
     * @return
     */
    private void gitResolver(AppServiceVersionDownloadPayload appServiceVersionPayload,
                             Boolean isFirst,
                             String groupPath,
                             File file,
                             AppServiceDownloadPayload downloadPayload,
                             String accessToken,
                             AppServiceVersionDTO versionDTO,
                             Long appServiceId) {
        Git git = null;
        String repoUrl = !gitlabUrl.endsWith("/") ? gitlabUrl + "/" : gitlabUrl;
        String repositoryUrl = repoUrl + groupPath + "/" + downloadPayload.getAppServiceCode() + GIT;
        String unZipFilePath = gitUtil.getWorkingDirectory(REPO + System.currentTimeMillis());
        FileUtil.unZipFiles(file, unZipFilePath);
        unZipFilePath = String.format("%s%s%s", unZipFilePath, File.separator, appServiceVersionPayload.getVersion());
        if (isFirst) {
            git = gitUtil.initGit(new File(unZipFilePath));
        } else {
            String appServiceDir = APPLICATION + System.currentTimeMillis();
            String appServiceFilePath = gitUtil.clone(appServiceDir, repositoryUrl, accessToken);
            git = gitUtil.combineAppMarket(appServiceFilePath, unZipFilePath);
        }
        //6. push 到远程仓库
        gitUtil.commitAndPushForMaster(git, repositoryUrl, appServiceVersionPayload.getVersion(), accessToken);

        if (versionDTO.getId() == null) {
            versionDTO.setAppServiceId(appServiceId);
            BeanUtils.copyProperties(appServiceVersionPayload, versionDTO);
            versionDTO = appServiceVersionService.baseCreate(versionDTO);
        }
        versionDTO.setCommit(gitUtil.getFirstCommit(git));
        AppServiceVersionDTO appServiceVersionDTO = appServiceVersionService.baseQueryByAppIdAndVersion(appServiceId, appServiceVersionPayload.getVersion());
        appServiceVersionDTO.setCommit(gitUtil.getFirstCommit(git));
        appServiceVersionService.baseUpdate(appServiceVersionDTO);
    }

    /**
     * chart 解析 下载
     *
     * @param appServiceVersionPayload
     * @param appServiceId
     * @param appServiceCode
     * @param file                     chart文件
     * @return
     */
    @Transactional(rollbackFor = Exception.class)
    private void chartResolver(AppServiceVersionDownloadPayload appServiceVersionPayload, Long appServiceId, String appServiceCode, File file, AppServiceVersionDTO versionDTO) {
        String unZipPath = String.format(APP_TEMP_PATH_FORMAT, file.getParentFile().getAbsolutePath(), File.separator, System.currentTimeMillis());
        FileUtil.createDirectory(unZipPath);
        FileUtil.unTarGZ(file, unZipPath);
        File zipDirectory = new File(String.format(APP_TEMP_PATH_FORMAT, unZipPath, File.separator, appServiceCode));
        helmUrl = helmUrl.endsWith("/") ? helmUrl : helmUrl + "/";
        versionDTO.setAppServiceId(appServiceId);
        //解析 解压过后的文件
        if (zipDirectory.exists() && zipDirectory.isDirectory()) {

            File[] listFiles = zipDirectory.listFiles();
            BeanUtils.copyProperties(appServiceVersionPayload, versionDTO);
            //9. 获取替换Repository
            List<File> appMarkets = Arrays.stream(listFiles).parallel()
                    .filter(k -> k.getName().equals(VALUES))
                    .collect(Collectors.toCollection(ArrayList::new));
            if (!appMarkets.isEmpty() && appMarkets.size() == 1) {
                File valuesFile = appMarkets.get(0);

                Map<String, String> params = new HashMap<>();
                params.put(appServiceVersionPayload.getImage(), String.format("%s/%s/%s", harborUrl, MARKET_PRO, appServiceCode));
                FileUtil.fileToInputStream(valuesFile, params);

                //10. 创建appServiceValue
                AppServiceVersionValueDTO versionValueDTO = new AppServiceVersionValueDTO();
                versionValueDTO.setValue(FileUtil.getFileContent(valuesFile));
                versionDTO.setValueId(appServiceVersionValueService.baseCreate(versionValueDTO).getId());
                // 创建ReadMe
                AppServiceVersionReadmeDTO versionReadmeDTO = new AppServiceVersionReadmeDTO();
                versionReadmeDTO.setReadme(FileUtil.getReadme(unZipPath));
                versionDTO.setReadmeValueId(appServiceVersionReadmeService.baseCreate(versionReadmeDTO).getId());
                //创建version
                appServiceVersionService.baseCreate(versionDTO);
            }
        } else {
            FileUtil.deleteDirectory(zipDirectory);
            throw new CommonException("error.zip.empty");
        }

        String newTgzFile = gitUtil.getWorkingDirectory(CHART + System.currentTimeMillis());
        FileUtil.toTgz(String.format("%s%s%s", unZipPath, File.separator, appServiceCode), newTgzFile);
        chartUtil.uploadChart(MARKET, DOWNLOADED_APP, new File(newTgzFile + TGZ));
        FileUtil.deleteFile(file);
        FileUtil.deleteDirectory(new File(unZipPath));
        FileUtil.deleteDirectory(new File(newTgzFile));

        versionDTO.setRepository(String.format("%s/%s/%s", harborUrl, MARKET_PRO, appServiceCode));
        appServiceVersionService.baseUpdate(versionDTO);
    }

    /**
     * 源码打包
     *
     * @param appServiceMarketVO
     * @param appFilePath
     * @param iamUserId
     */
    private void packageRepo(AppServiceUploadVO appServiceMarketVO, String appFilePath, Long iamUserId) {
        AppServiceDTO appServiceDTO = appServiceMapper.selectByPrimaryKey(appServiceMarketVO.getAppServiceId());
        ApplicationDTO applicationDTO = baseServiceClientOperator.queryAppById(appServiceDTO.getAppId());
        OrganizationDTO organizationDTO = baseServiceClientOperator.queryOrganizationById(applicationDTO.getOrganizationId());

        //1.创建目录 应用服务仓库
        FileUtil.createDirectory(appFilePath, appServiceDTO.getCode());
        String appServiceRepositoryPath = String.format("%s/%s", appFilePath, appServiceDTO.getCode());

        String repoUrl = !gitlabUrl.endsWith("/") ? gitlabUrl + "/" : gitlabUrl;
        UserAttrDTO userAttrDTO = userAttrService.baseQueryById(iamUserId);
        String newToken = appServiceService.getToken(appServiceDTO.getGitlabProjectId(), appFilePath, userAttrDTO);
        appServiceDTO.setRepoUrl(repoUrl + organizationDTO.getCode()
                + "-" + applicationDTO.getCode() + "/" + appServiceDTO.getCode() + GIT);
        appServiceMarketVO.getAppServiceVersionUploadVOS().forEach(appServiceMarketVersionVO -> {
            AppServiceVersionDTO appServiceVersionDTO = appServiceVersionService.baseQuery(appServiceMarketVersionVO.getId());
            //2. 创建目录 应用服务版本

            FileUtil.createDirectory(appServiceRepositoryPath, appServiceVersionDTO.getVersion());
            String appServiceVersionPath = String.format("%s/%s", appServiceRepositoryPath, appServiceVersionDTO.getVersion());

            //3.clone源码,checkout到版本所在commit，并删除.git文件
            gitUtil.cloneAndCheckout(appServiceVersionPath, appServiceDTO.getRepoUrl(), newToken, appServiceVersionDTO.getCommit());
            toZip(String.format("%s%s", appServiceVersionPath, ZIP), appServiceVersionPath);
            FileUtil.deleteDirectory(new File(appServiceVersionPath));
        });
    }

    /**
     * chart打包
     *
     * @param appServiceMarketVO
     * @param appFilePath
     */
    private void packageChart(AppServiceUploadVO appServiceMarketVO, String appFilePath) {
        AppServiceDTO appServiceDTO = appServiceMapper.selectByPrimaryKey(appServiceMarketVO.getAppServiceId());
        ProjectDTO projectDTO = baseServiceClientOperator.queryIamProjectById(appServiceDTO.getAppId());
        OrganizationDTO organizationDTO = baseServiceClientOperator.queryOrganizationById(projectDTO.getOrganizationId());

        //1.创建目录 应用服务仓库
        FileUtil.createDirectory(appFilePath, appServiceDTO.getCode());
        String appServiceChartPath = String.format(APP_TEMP_PATH_FORMAT, appFilePath, File.separator, appServiceDTO.getCode());
        appServiceMarketVO.getAppServiceVersionUploadVOS().forEach(appServiceMarketVersionVO -> {
            //2.下载chart
            AppServiceVersionDTO appServiceVersionDTO = appServiceVersionService.baseQuery(appServiceMarketVersionVO.getId());
            chartUtil.downloadChart(appServiceVersionDTO, organizationDTO, projectDTO, appServiceDTO, appServiceChartPath);
            analysisChart(appServiceChartPath, appServiceDTO.getCode(), appServiceVersionDTO, appServiceMarketVO.getHarborUrl());
        });
    }

    /**
     * chart解析 上传
     *
     * @param zipPath
     * @param appServiceCode
     * @param appServiceVersionDTO
     * @param harborUrl
     */
    private void analysisChart(String zipPath, String appServiceCode, AppServiceVersionDTO appServiceVersionDTO, String harborUrl) {
        String tgzFileName = String.format("%s%s%s-%s.tgz",
                zipPath,
                File.separator,
                appServiceCode,
                appServiceVersionDTO.getVersion());
        FileUtil.unTarGZ(tgzFileName, zipPath);
        FileUtil.deleteFile(tgzFileName);

        String unTarGZPath = String.format(APP_TEMP_PATH_FORMAT, zipPath, File.separator, appServiceCode);
        File zipDirectory = new File(unTarGZPath);
        //解析 解压过后的文件
        if (zipDirectory.exists() && zipDirectory.isDirectory()) {
            File[] listFiles = zipDirectory.listFiles();
            //获取替换Repository
            List<File> appMarkets = Arrays.stream(listFiles).parallel()
                    .filter(k -> VALUES.equals(k.getName()))
                    .collect(Collectors.toCollection(ArrayList::new));
            if (!appMarkets.isEmpty() && appMarkets.size() == 1) {
                Map<String, String> params = new HashMap<>();
                String image = appServiceVersionDTO.getImage().replace(":" + appServiceVersionDTO.getVersion(), "");
                params.put(image, String.format("%s/%s", harborUrl, appServiceVersionDTO.getVersion()));
                FileUtil.fileToInputStream(appMarkets.get(0), params);
            }
        } else {
            FileUtil.deleteDirectory(new File(zipPath).getParentFile());
            throw new CommonException("error.chart.empty");
        }
        // 打包
        String newChartFilePath = String.format(APP_TEMP_PATH_FORMAT, zipPath, File.separator, appServiceVersionDTO.getVersion());
        FileUtil.toTgz(unTarGZPath, newChartFilePath);
        FileUtil.deleteDirectory(new File(unTarGZPath));
    }

    private void toZip(String outputPath, String filePath) {
        try {
            FileOutputStream outputStream = new FileOutputStream(new File(outputPath));
            FileUtil.toZip(filePath, outputStream, true);
            FileUtil.deleteDirectory(new File(filePath));
        } catch (FileNotFoundException e) {
            throw new CommonException("error.zip.repository", e.getMessage());
        }
    }

    private Map<String, String> pushImageForUpload(AppMarketUploadVO appMarketUploadVO) {
        Map<String, String> iamgeMap = new HashMap<>();

        //获取push_image 脚本目录
        String shellPath = new File(this.getClass().getResource(SHELL).getPath()).getAbsolutePath();
        // 创建images
        appMarketUploadVO.getAppServiceUploadVOS().forEach(appServiceMarketVO -> {
            StringBuilder stringBuilder = new StringBuilder();
            appServiceMarketVO.getAppServiceVersionUploadVOS().forEach(t -> {
                stringBuilder.append(appServiceVersionService.baseQuery(t.getId()).getImage());
                stringBuilder.append(System.getProperty(LINE));
                iamgeMap.put(String.format("%s-%s", appServiceMarketVO.getAppServiceCode(), t.getVersion()), String.format("%s:%s", appServiceMarketVO.getHarborUrl(), t.getVersion()));
            });
            FileUtil.saveDataToFile(shellPath, IMAGES, stringBuilder.toString());

            //获取原仓库配置
            ConfigVO configVO = devopsConfigService.queryByResourceId(
                    appServiceService.baseQuery(appServiceMarketVO.getAppServiceId()).getChartConfigId(), "harbor")
                    .get(0).getConfig();
            User user = new User();
            BeanUtils.copyProperties(configVO, user);
            user.setUsername(configVO.getUserName());

            // 执行脚本
            callScript(shellPath, appServiceMarketVO.getHarborUrl(), appMarketUploadVO.getUser(), user);
            FileUtil.deleteFile(String.format(APP_TEMP_PATH_FORMAT, shellPath, File.separator, IMAGES));
        });
        return iamgeMap;
    }

    private void pushImageForDownload(AppMarketDownloadPayload appMarketDownloadVO) {
        //获取push_image 脚本目录
        String shellPath = this.getClass().getResource(SHELL).getPath();

        appMarketDownloadVO.getAppServiceDownloadPayloads().forEach(appServiceMarketVO -> {
            StringBuilder stringBuilder = new StringBuilder();
            appServiceMarketVO.getAppServiceVersionDownloadPayloads().forEach(t -> {
                stringBuilder.append(t.getImage());
                stringBuilder.append(System.getProperty(LINE));
            });
            FileUtil.saveDataToFile(shellPath, IMAGES, stringBuilder.toString());

            //获取新仓库配置
            ConfigVO configVO = gson.fromJson(devopsConfigService.baseQueryByName(null, HARBOR_NAME).getConfig(), ConfigVO.class);
            User user = new User();
            BeanUtils.copyProperties(configVO, user);
            user.setUsername(configVO.getUserName());
            harborUrl = harborUrl.endsWith("/") ? harborUrl : harborUrl + "/";

            callScript(new File(shellPath).getAbsolutePath(), String.format("%s%s", harborUrl, MARKET_PRO), user, appMarketDownloadVO.getUser());
            FileUtil.deleteFile(String.format(APP_TEMP_PATH_FORMAT, shellPath, File.separator, IMAGES));
        });
    }

    /**
     * 脚本文件具体执行及脚本执行过程探测
     *
     * @param script 脚本文件绝对路径
     */
    private void callScript(String script, String harborUrl, User newUser, User oldUser) {
        try {
            String cmd = String.format("cd %s \n" +
                            " sh %s %s %s %s %s %s",
                    script, PUSH_IAMGES, harborUrl, newUser.getUsername(), newUser.getPassword(), oldUser.getUsername(), oldUser.getPassword());
            LOGGER.info(cmd);
            //执行脚本并等待脚本执行完成
            Process process = Runtime.getRuntime().exec(cmd);

            //写出脚本执行中的过程信息
            BufferedReader infoInput = new BufferedReader(new InputStreamReader(process.getInputStream()));
            BufferedReader errorInput = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            String line = "";
            while ((line = infoInput.readLine()) != null) {
                LOGGER.info(line);
            }
            while ((line = errorInput.readLine()) != null) {
                LOGGER.error(line);
            }
            infoInput.close();
            errorInput.close();

            //阻塞执行线程直至脚本执行完成后返回
            process.waitFor();
        } catch (Exception e) {
            throw new CommonException("error.exec.push.image");
        }
    }

    private void fileUpload(List<String> zipFileList, AppMarketUploadVO appMarketUploadVO, Map map) {
        List<MultipartBody.Part> files = new ArrayList<>();
        zipFileList.forEach(f -> {
            File file = new File(f);
            RequestBody requestFile = RequestBody.create(MediaType.parse("multipart/form-data"), file);
            MultipartBody.Part body = MultipartBody.Part.createFormData("file", file.getName(), requestFile);
            files.add(body);
        });
        String mapJson = !map.isEmpty() ? gson.toJson(map) : null;
        ConfigurationProperties configurationProperties = new ConfigurationProperties();
        String getawayUrl = appMarketUploadVO.getSaasGetawayUrl().endsWith("/") ? appMarketUploadVO.getSaasGetawayUrl() : appMarketUploadVO.getSaasGetawayUrl() + "/";
        configurationProperties.setBaseUrl(getawayUrl);
        configurationProperties.setInsecureSkipTlsVerify(false);
        configurationProperties.setType(MARKET);
        Retrofit retrofit = RetrofitHandler.initRetrofit(configurationProperties);
        MarketServiceClient marketServiceClient = retrofit.create(MarketServiceClient.class);
        try {
            marketServiceClient.uploadFile(appMarketUploadVO.getAppVersion(), mapJson, files).execute();
        } catch (IOException e) {
            throw new CommonException("error.upload.file", e.getMessage());
        }
    }

    private void fileDownload(String fileUrl, String downloadFilePath) {
        ConfigurationProperties configurationProperties = new ConfigurationProperties();
        configurationProperties.setType(CHART);
        configurationProperties.setInsecureSkipTlsVerify(false);
        List<String> fileUrlList = Arrays.asList(fileUrl.split("/"));
        String fileName = fileUrlList.get(fileUrlList.size() - 1);
        fileUrl = fileUrl.replace(fileName, "");
        configurationProperties.setBaseUrl(fileUrl);

        Retrofit retrofit = RetrofitHandler.initRetrofit(configurationProperties);
        MarketServiceClient marketServiceClient = retrofit.create(MarketServiceClient.class);
        Call<ResponseBody> getTaz = marketServiceClient.downloadFile(fileName);
        FileOutputStream fos = null;
        try {
            Response<ResponseBody> response = getTaz.execute();
            fos = new FileOutputStream(downloadFilePath);
            if (response.body() != null) {
                InputStream is = response.body().byteStream();
                byte[] buffer = new byte[4096];
                int r = 0;
                while ((r = is.read(buffer)) > 0) {
                    fos.write(buffer, 0, r);
                }
                is.close();
            }
            fos.close();
        } catch (IOException e) {
            IOUtils.closeQuietly(fos);
            throw new CommonException("error.download.file", e.getMessage());
        }
    }

    private AppServiceUploadVO dtoToMarketVO(AppServiceDTO applicationDTO) {
        AppServiceUploadVO appServiceMarketVO = new AppServiceUploadVO();
        appServiceMarketVO.setAppServiceId(applicationDTO.getId());
        appServiceMarketVO.setAppServiceCode(applicationDTO.getCode());
        appServiceMarketVO.setAppServiceName(applicationDTO.getName());
        return appServiceMarketVO;
    }
}
