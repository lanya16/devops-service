package io.choerodon.devops.app.service.impl;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.github.pagehelper.PageInfo;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import io.choerodon.base.domain.PageRequest;
import io.choerodon.core.convertor.ConvertHelper;
import io.choerodon.core.convertor.ConvertPageHelper;
import io.choerodon.core.exception.CommonException;
import io.choerodon.devops.api.dto.AppMarketDownloadDTO;
import io.choerodon.devops.api.dto.AppMarketTgzDTO;
import io.choerodon.devops.api.dto.AppMarketVersionDTO;
import io.choerodon.devops.api.dto.AppVersionAndValueDTO;
import io.choerodon.devops.api.dto.ApplicationReleasingDTO;
import io.choerodon.devops.api.dto.ApplicationVersionRepDTO;
import io.choerodon.devops.app.service.AppShareService;
import io.choerodon.devops.domain.application.entity.AppShareResourceE;
import io.choerodon.devops.domain.application.entity.ApplicationE;
import io.choerodon.devops.domain.application.entity.ApplicationVersionE;
import io.choerodon.devops.domain.application.entity.ApplicationVersionValueE;
import io.choerodon.devops.domain.application.entity.DevopsAppShareE;
import io.choerodon.devops.domain.application.entity.ProjectE;
import io.choerodon.devops.domain.application.factory.ApplicationMarketFactory;
import io.choerodon.devops.domain.application.repository.AppShareRecouceRepository;
import io.choerodon.devops.domain.application.repository.AppShareRepository;
import io.choerodon.devops.domain.application.repository.ApplicationRepository;
import io.choerodon.devops.domain.application.repository.ApplicationVersionRepository;
import io.choerodon.devops.domain.application.repository.ApplicationVersionValueRepository;
import io.choerodon.devops.domain.application.repository.DevopsProjectConfigRepository;
import io.choerodon.devops.domain.application.repository.IamRepository;
import io.choerodon.devops.domain.application.valueobject.Organization;
import io.choerodon.devops.infra.common.util.ChartUtil;
import io.choerodon.devops.infra.common.util.FileUtil;
import io.choerodon.devops.infra.common.util.GenerateUUID;
import io.choerodon.devops.infra.config.HarborConfigurationProperties;
import io.choerodon.devops.infra.dataobject.DevopsAppMarketVersionDO;
import io.choerodon.devops.infra.dataobject.DevopsAppShareDO;
import io.choerodon.websocket.tool.UUIDTool;

/**
 * Created by ernst on 2018/5/12.
 */
@Service
public class AppShareServiceImpl implements AppShareService {
    private static final String CHARTS = "charts";
    private static final String CHART = "chart";
    private static final String ORGANIZATION = "organization";
    private static final String PUBLIC = "public";
    private static final String IMAGES = "images";
    private static final String PUSH_IAMGES = "push_image.sh";
    private static final String JSON_FILE = ".json";

    private static final String FILE_SEPARATOR = "/";
    private static final Logger logger = LoggerFactory.getLogger(AppShareServiceImpl.class);

    private static Gson gson = new Gson();

    @Value("${services.gitlab.url}")
    private String gitlabUrl;
    @Value("${services.helm.url}")
    private String helmUrl;

    @Autowired
    private ApplicationVersionRepository applicationVersionRepository;
    @Autowired
    private AppShareRepository appShareRepository;
    @Autowired
    private AppShareRecouceRepository appShareRecouceRepository;
    @Autowired
    private IamRepository iamRepository;
    @Autowired
    private ApplicationRepository applicationRepository;
    @Autowired
    private HarborConfigurationProperties harborConfigurationProperties;
    @Autowired
    private ApplicationVersionValueRepository applicationVersionValueRepository;
    @Autowired
    private DevopsProjectConfigRepository devopsProjectConfigRepository;
    @Autowired
    private ChartUtil chartUtil;

    @Override
    public Long release(Long projectId, ApplicationReleasingDTO applicationReleasingDTO) {
        List<Long> ids;
        if (applicationReleasingDTO != null) {
            String publishLevel = applicationReleasingDTO.getPublishLevel();
            if (!ORGANIZATION.equals(publishLevel) && !PUBLIC.equals(publishLevel)) {
                throw new CommonException("error.publishLevel");
            }
            List<AppMarketVersionDTO> appVersions = applicationReleasingDTO.getAppVersions();
            ids = appVersions.stream().map(AppMarketVersionDTO::getId)
                    .collect(Collectors.toCollection(ArrayList::new));
        } else {
            throw new CommonException("error.app.check");
        }
        appShareRepository.checkCanPub(applicationReleasingDTO.getAppId());
        //校验应用和版本
        applicationVersionRepository.checkAppAndVersion(applicationReleasingDTO.getAppId(), ids);
        applicationVersionRepository.updatePublishLevelByIds(ids, 1L);

        DevopsAppShareE devopsAppShareE = ApplicationMarketFactory.create();
        devopsAppShareE.initApplicationEById(applicationReleasingDTO.getAppId());
        devopsAppShareE.setPublishLevel(applicationReleasingDTO.getPublishLevel());
        devopsAppShareE.setActive(true);
        devopsAppShareE.setContributor(applicationReleasingDTO.getContributor());
        devopsAppShareE.setDescription(applicationReleasingDTO.getDescription());
        devopsAppShareE.setCategory(applicationReleasingDTO.getCategory());
        devopsAppShareE.setImgUrl(applicationReleasingDTO.getImgUrl());
        devopsAppShareE.setFree(applicationReleasingDTO.getFree());
        devopsAppShareE = appShareRepository.create(devopsAppShareE);
        Long shareId = devopsAppShareE.getId();
        if ("projects".equals(applicationReleasingDTO.getPublishLevel())) {
            applicationReleasingDTO.getProjectIds().forEach(t -> appShareRecouceRepository.create(new AppShareResourceE(shareId, t)));
        }
        return appShareRepository.getMarketIdByAppId(applicationReleasingDTO.getAppId());
    }

    @Override
    public PageInfo<ApplicationReleasingDTO> listMarketAppsByProjectId(Long projectId, PageRequest pageRequest,
                                                                       String searchParam) {
        PageInfo<ApplicationReleasingDTO> applicationMarketEPage = ConvertPageHelper.convertPageInfo(
                appShareRepository.listMarketAppsByProjectId(
                        projectId, pageRequest, searchParam),
                ApplicationReleasingDTO.class);
        List<ApplicationReleasingDTO> appShareEList = applicationMarketEPage.getList();
        appShareEList.forEach(t -> {
            if ("projects".equals(t.getPublishLevel())) {
                t.setProjectIds(appShareRecouceRepository.queryByShareId(t.getId()).stream().map(AppShareResourceE::getProjectId).collect(Collectors.toList()));
            }
        });
        applicationMarketEPage.setList(appShareEList);
        return applicationMarketEPage;
    }

    @Override
    public PageInfo<ApplicationReleasingDTO> listMarketAppsBySite(String publishLevel, PageRequest pageRequest, String searchParam) {
        PageInfo<DevopsAppShareE> applicationMarketEPage = appShareRepository.listMarketAppsBySite(publishLevel, pageRequest, searchParam);
        return ConvertPageHelper.convertPageInfo(
                applicationMarketEPage,
                ApplicationReleasingDTO.class);
    }

    @Override
    public ApplicationReleasingDTO getAppDetailByShareId(Long shareId) {
        return getMarketAppInProject(null, shareId);
    }

    @Override
    public List<Long> batchRelease(List<ApplicationReleasingDTO> releasingDTOList) {
        return releasingDTOList.stream().map(releasingDTO -> {
            if ("site".equals(releasingDTO.getPublishLevel())) {
                return release(null, releasingDTO);
            } else {
                throw new CommonException("error.release.app.to.site");
            }
        }).collect(Collectors.toList());
    }

    @Override
    public PageInfo<ApplicationReleasingDTO> getAppsDetail(PageRequest pageRequest, String params, List<Long> shareIds) {
        return ConvertPageHelper.convertPageInfo(
                appShareRepository.queryByShareIds(pageRequest, params, shareIds),
                ApplicationReleasingDTO.class);
    }

    @Override
    public PageInfo<ApplicationVersionRepDTO> getVersionsByAppId(Long appId, PageRequest pageRequest, String params) {
        return ConvertPageHelper.convertPageInfo(
                applicationVersionRepository.listByAppIdAndParamWithPage(appId, true, null, pageRequest, params), ApplicationVersionRepDTO.class);
    }

    @Override
    public AppVersionAndValueDTO getValuesAndChart(Long versionId) {
        AppVersionAndValueDTO appVersionAndValueDTO = new AppVersionAndValueDTO();
        String versionValue = FileUtil.checkValueFormat(applicationVersionRepository.queryValue(versionId));
        try {
            FileUtil.checkYamlFormat(versionValue);
        } catch (Exception e) {
            appVersionAndValueDTO.setValues(versionValue);
        }
        ApplicationVersionE applicationVersionE =
                applicationVersionRepository.query(versionId);
        appVersionAndValueDTO.setRepository(applicationVersionE.getRepository());
        appVersionAndValueDTO.setVersion(applicationVersionE.getVersion());
        return appVersionAndValueDTO;
    }

    @Override
    public void updateByShareId(Long shareId, Boolean isFree) {
        DevopsAppShareDO devopsAppShareDO = new DevopsAppShareDO();
        devopsAppShareDO.setId(shareId);
        devopsAppShareDO.setFree(isFree);
        appShareRepository.update(devopsAppShareDO);
    }

    @Override
    public PageInfo<ApplicationReleasingDTO> listMarketApps(Long projectId, PageRequest pageRequest, String searchParam) {
        ProjectE projectE = iamRepository.queryIamProject(projectId);
        if (projectE != null && projectE.getOrganization() != null) {
            Long organizationId = projectE.getOrganization().getId();
            List<ProjectE> projectEList = iamRepository.listIamProjectByOrgId(organizationId, null, null);
            List<Long> projectIds = new ArrayList<>();
            if (projectEList != null) {
                projectIds = projectEList.stream().map(ProjectE::getId).collect(Collectors.toList());
            }
            PageInfo<DevopsAppShareE> applicationMarketEPage = appShareRepository.listMarketApps(
                    projectIds, pageRequest, searchParam);

            return ConvertPageHelper.convertPageInfo(
                    applicationMarketEPage,
                    ApplicationReleasingDTO.class);
        }
        return null;
    }

    @Override
    public ApplicationReleasingDTO getMarketAppInProject(Long projectId, Long appMarketId) {
        DevopsAppShareE applicationMarketE =
                appShareRepository.getMarket(projectId, appMarketId);
        List<DevopsAppMarketVersionDO> versionDOList = appShareRepository
                .getVersions(projectId, appMarketId, true);
        List<AppMarketVersionDTO> appMarketVersionDTOList = ConvertHelper
                .convertList(versionDOList, AppMarketVersionDTO.class);
        ApplicationReleasingDTO applicationReleasingDTO =
                ConvertHelper.convert(applicationMarketE, ApplicationReleasingDTO.class);
        applicationReleasingDTO.setAppVersions(appMarketVersionDTOList);

        return applicationReleasingDTO;
    }

    @Override
    public ApplicationReleasingDTO getMarketApp(Long appMarketId, Long versionId) {
        DevopsAppShareE applicationMarketE =
                appShareRepository.getMarket(null, appMarketId);
        ApplicationE applicationE = applicationMarketE.getApplicationE();
        List<DevopsAppMarketVersionDO> versionDOList = appShareRepository
                .getVersions(null, appMarketId, true);
        List<AppMarketVersionDTO> appMarketVersionDTOList = ConvertHelper
                .convertList(versionDOList, AppMarketVersionDTO.class)
                .stream()
                .sorted(this::compareAppMarketVersionDTO)
                .collect(Collectors.toCollection(ArrayList::new));
        ApplicationReleasingDTO applicationReleasingDTO =
                ConvertHelper.convert(applicationMarketE, ApplicationReleasingDTO.class);
        applicationReleasingDTO.setAppVersions(appMarketVersionDTOList);

        Long applicationId = applicationE.getId();
        applicationE = applicationRepository.query(applicationId);

        Date latestUpdateDate = appMarketVersionDTOList.isEmpty()
                ? getLaterDate(applicationE.getLastUpdateDate(), applicationMarketE.getMarketUpdatedDate())
                : getLatestDate(
                appMarketVersionDTOList.get(0).getUpdatedDate(),
                applicationE.getLastUpdateDate(),
                applicationMarketE.getMarketUpdatedDate());
        applicationReleasingDTO.setLastUpdatedDate(latestUpdateDate);

        Boolean versionExist = appMarketVersionDTOList.stream().anyMatch(t -> t.getId().equals(versionId));
        Long latestVersionId = versionId;
        if (!versionExist) {
            Optional<AppMarketVersionDTO> optional = appMarketVersionDTOList.stream()
                    .max(this::compareAppMarketVersionDTO);
            latestVersionId = optional.isPresent()
                    ? optional.get().getId()
                    : versionId;
        }
        ApplicationVersionE applicationVersionE = applicationVersionRepository.query(latestVersionId);
        String readme = applicationVersionRepository
                .getReadme(applicationVersionE.getApplicationVersionReadmeV().getId());

        applicationReleasingDTO.setReadme(readme);

        return applicationReleasingDTO;
    }

    private Date getLatestDate(Date a, Date b, Date c) {
        if (a.after(b)) {
            return getLaterDate(a, c);
        } else {
            return getLaterDate(b, c);
        }
    }

    private Date getLaterDate(Date a, Date b) {
        return a.after(b) ? a : b;
    }

    private Integer compareAppMarketVersionDTO(AppMarketVersionDTO s, AppMarketVersionDTO t) {
        if (s.getUpdatedDate().before(t.getUpdatedDate())) {
            return 1;
        } else {
            if (s.getUpdatedDate().after(t.getUpdatedDate())) {
                return -1;
            } else {
                if (s.getCreationDate().before(t.getCreationDate())) {
                    return 1;
                } else {
                    return s.getCreationDate().after(t.getCreationDate()) ? -1 : 0;
                }
            }
        }
    }

    @Override
    public String getMarketAppVersionReadme(Long appMarketId, Long versionId) {
        appShareRepository.checkMarketVersion(appMarketId, versionId);
        ApplicationVersionE applicationVersionE = applicationVersionRepository.query(versionId);
        return applicationVersionRepository.getReadme(applicationVersionE.getApplicationVersionReadmeV().getId());
    }

    @Override
    public void unpublish(Long projectId, Long appMarketId) {
        appShareRepository.checkProject(projectId, appMarketId);
        appShareRepository.checkDeployed(projectId, appMarketId, null, null);
        appShareRepository.unpublishApplication(appMarketId);
    }

    @Override
    public void unpublish(Long projectId, Long appMarketId, Long versionId) {
        appShareRepository.checkProject(projectId, appMarketId);
        appShareRepository.checkDeployed(projectId, appMarketId, versionId, null);
        appShareRepository.unpublishVersion(appMarketId, versionId);

    }

    @Override
    public void update(Long projectId, Long appMarketId, ApplicationReleasingDTO applicationRelease) {
        if (applicationRelease != null) {
            String publishLevel = applicationRelease.getPublishLevel();
            if (publishLevel != null
                    && !ORGANIZATION.equals(publishLevel)
                    && !PUBLIC.equals(publishLevel)) {
                throw new CommonException("error.publishLevel");
            }
        } else {
            throw new CommonException("error.app.check");
        }
        if (applicationRelease.getId() != null
                && !appMarketId.equals(applicationRelease.getId())) {
            throw new CommonException("error.id.notMatch");
        }
        appShareRepository.checkProject(projectId, appMarketId);
        ApplicationReleasingDTO applicationReleasingDTO = getMarketAppInProject(projectId, appMarketId);
        if (applicationRelease.getAppId() != null
                && !applicationReleasingDTO.getAppId().equals(applicationRelease.getAppId())) {
            throw new CommonException("error.app.cannot.change");
        }
        ProjectE projectE = iamRepository.queryIamProject(projectId);
        if (projectE == null || projectE.getOrganization() == null) {
            throw new CommonException("error.project.query");
        }
        if (applicationRelease.getPublishLevel() != null
                && !applicationRelease.getPublishLevel().equals(applicationReleasingDTO.getPublishLevel())) {
            throw new CommonException("error.publishLevel.cannot.change");
        }
        DevopsAppShareDO devopsAppMarketDO = ConvertHelper.convert(applicationRelease, DevopsAppShareDO.class);
        if (!ConvertHelper.convert(applicationReleasingDTO, DevopsAppShareDO.class).equals(devopsAppMarketDO)) {
            appShareRepository.update(devopsAppMarketDO);
        }
    }

    @Override
    public void update(Long projectId, Long appMarketId, List<AppMarketVersionDTO> versionDTOList) {
        appShareRepository.checkProject(projectId, appMarketId);

        ApplicationReleasingDTO applicationReleasingDTO = getMarketAppInProject(projectId, appMarketId);

        List<Long> ids = versionDTOList.stream()
                .map(AppMarketVersionDTO::getId).collect(Collectors.toCollection(ArrayList::new));

        applicationVersionRepository.checkAppAndVersion(applicationReleasingDTO.getAppId(), ids);
        applicationVersionRepository.updatePublishLevelByIds(ids, 1L);
    }

    @Override
    public List<AppMarketVersionDTO> getAppVersions(Long projectId, Long appMarketId, Boolean isPublish) {
        return ConvertHelper.convertList(appShareRepository.getVersions(projectId, appMarketId, isPublish),
                AppMarketVersionDTO.class);
    }

    @Override
    public PageInfo<AppMarketVersionDTO> getAppVersions(Long projectId, Long appMarketId, Boolean isPublish,
                                                        PageRequest pageRequest, String searchParam) {
        return ConvertPageHelper.convertPageInfo(
                appShareRepository.getVersions(projectId, appMarketId, isPublish, pageRequest, searchParam),
                AppMarketVersionDTO.class);
    }

    @Override
    public AppMarketTgzDTO getMarketAppListInFile(Long projectId, MultipartFile file) {
        ProjectE projectE = iamRepository.queryIamProject(projectId);
        Organization organization = iamRepository.queryOrganizationById(projectE.getOrganization().getId());
        String dirName = UUIDTool.genUuid();
        String classPath = String.format(
                "tmp%s%s%s%s",
                FILE_SEPARATOR,
                organization.getCode(),
                FILE_SEPARATOR,
                projectE.getCode());

        String destPath = String.format("%s%s%s", classPath, FILE_SEPARATOR, dirName);
        String path = FileUtil.multipartFileToFileWithSuffix(destPath, file, ".zip");
        FileUtil.unZipFiles(new File(path), destPath);
        FileUtil.deleteFile(path);
        File zipDirectory = new File(destPath);
        AppMarketTgzDTO appMarketTgzDTO = new AppMarketTgzDTO();

        if (zipDirectory.exists() && zipDirectory.isDirectory()) {
            File[] chartsDirectory = zipDirectory.listFiles();
            if (chartsDirectory != null && chartsDirectory.length == 1) {
                File[] appFiles = chartsDirectory[0].listFiles();
                if (appFiles == null || appFiles.length == 0) {
                    FileUtil.deleteDirectory(zipDirectory);
                    throw new CommonException("error.file.empty");
                }

                List<File> appFileList = Arrays.stream(appFiles)
                        .filter(File::isDirectory).collect(Collectors.toCollection(ArrayList::new));
                // do sth with appFileList
                analyzeAppFile(appMarketTgzDTO.getAppMarketList(), appFileList);
            } else {
                FileUtil.deleteDirectory(zipDirectory);
                throw new CommonException("error.zip.illegal");
            }
        } else {
            FileUtil.deleteDirectory(zipDirectory);
            throw new CommonException("error.zip.empty");
        }
        appMarketTgzDTO.setFileCode(dirName);
        return appMarketTgzDTO;
    }

    @Override
    public Boolean importApps(Long projectId, String fileName, Boolean isPublic) {
        ProjectE projectE = iamRepository.queryIamProject(projectId);
        Organization organization = iamRepository.queryOrganizationById(projectE.getOrganization().getId());
        String destPath = String.format(
                "tmp%s%s%s%s%s%s",
                FILE_SEPARATOR,
                organization.getCode(),
                FILE_SEPARATOR,
                projectE.getCode(),
                FILE_SEPARATOR,
                fileName);
        File zipDirectory = new File(destPath);

        if (zipDirectory.exists() && zipDirectory.isDirectory()) {
            File[] chartsDirectory = zipDirectory.listFiles();
            File[] appFiles = chartsDirectory != null ? chartsDirectory[0].listFiles() : new File[0];
            if (appFiles == null || appFiles.length == 0) {
                FileUtil.deleteDirectory(zipDirectory);
                throw new CommonException("error.file.empty");
            }
            List<File> appFileList = Arrays.stream(appFiles)
                    .filter(File::isDirectory).collect(Collectors.toCollection(ArrayList::new));
            importAppFile(projectId, appFileList, isPublic);

        } else {
            throw new CommonException("error.zip.notFound");
        }
        FileUtil.deleteDirectory(zipDirectory);
        return true;
    }

    @Override
    public void deleteZip(Long projectId, String fileName) {
        ProjectE projectE = iamRepository.queryIamProject(projectId);
        Organization organization = iamRepository.queryOrganizationById(projectE.getOrganization().getId());
        String destPath = String.format(
                "tmp%s%s%s%s%s%s",
                FILE_SEPARATOR,
                organization.getCode(),
                FILE_SEPARATOR,
                projectE.getCode(),
                FILE_SEPARATOR,
                fileName);
        File zipDirectory = new File(destPath);
        FileUtil.deleteDirectory(zipDirectory);
    }

    private void analyzeAppFile(List<ApplicationReleasingDTO> appMarketVersionDTOS,
                                List<File> appFileList) {
        appFileList.forEach(t -> {
            String appName = t.getName();
            File[] appFiles = t.listFiles();
            if (appFiles != null && !appFileList.isEmpty()) {
                String appFileName = String.format("%s%s", appName, JSON_FILE);
                List<File> appMarkets = Arrays.stream(appFiles).parallel()
                        .filter(k -> k.getName().equals(appFileName))
                        .collect(Collectors.toCollection(ArrayList::new));
                if (!appMarkets.isEmpty() && appMarkets.size() == 1) {
                    File appMarket = appMarkets.get(0);
                    String appMarketJson = FileUtil.getFileContent(appMarket);
                    ApplicationReleasingDTO appMarketVersionDTO =
                            gson.fromJson(appMarketJson, ApplicationReleasingDTO.class);
                    appMarketVersionDTOS.add(appMarketVersionDTO);
                }
            }
        });
    }

    private void importAppFile(Long projectId, List<File> appFileList, Boolean isPublic) {
        ProjectE projectE = iamRepository.queryIamProject(projectId);
        Organization organization = iamRepository.queryOrganizationById(projectE.getOrganization().getId());
        String orgCode = organization.getCode();
        String projectCode = projectE.getCode();
        appFileList.stream().forEach(t -> {
            String appName = t.getName();
            File[] appFiles = t.listFiles();
            if (appFiles != null && !appFileList.isEmpty()) {
                String appFileName = String.format("%s%s", appName, JSON_FILE);
                List<File> appMarkets = Arrays.stream(appFiles).parallel()
                        .filter(k -> k.getName().equals(appFileName))
                        .collect(Collectors.toCollection(ArrayList::new));
                if (appMarkets != null && !appMarkets.isEmpty() && appMarkets.size() == 1) {
                    File appMarket = appMarkets.get(0);
                    String appMarketJson = FileUtil.getFileContent(appMarket);
                    ApplicationReleasingDTO applicationReleasingDTO =
                            gson.fromJson(appMarketJson, ApplicationReleasingDTO.class);
                    ApplicationE applicationE = new ApplicationE();
                    String appCode = applicationReleasingDTO.getCode();
                    applicationE.setName(applicationReleasingDTO.getName());
                    applicationE.setIsSkipCheckPermission(true);
                    applicationE.setType("normal");
                    Long appId = createOrUpdateApp(applicationE, appCode, projectId);
                    Boolean isVersionPublish = isPublic != null;
                    applicationReleasingDTO.getAppVersions().stream()
                            .forEach(appVersion -> createVersion(
                                    appVersion, orgCode, projectCode, appCode, appId, appFiles, isVersionPublish
                            ));
                    // 发布应用
                    releaseApp(isPublic, applicationReleasingDTO, appId);
                }
            }
        });
    }

    /**
     * 导出应用市场应用 zip
     *
     * @param appMarkets 应用市场应用信息
     */
    public void export(List<AppMarketDownloadDTO> appMarkets, String fileName) {
        List<String> images = new ArrayList<>();
        for (AppMarketDownloadDTO appMarketDownloadDTO : appMarkets) {
            ApplicationReleasingDTO applicationReleasingDTO = getMarketApp(appMarketDownloadDTO.getAppMarketId(), null);
            String destpath = String.format("charts%s%s",
                    FILE_SEPARATOR,
                    applicationReleasingDTO.getCode());
            ApplicationE applicationE = applicationRepository.query(applicationReleasingDTO.getAppId());
            ProjectE projectE = iamRepository.queryIamProject(applicationE.getProjectE().getId());
            Organization organization = iamRepository.queryOrganizationById(projectE.getOrganization().getId());
            applicationReleasingDTO.setAppVersions(
                    applicationReleasingDTO.getAppVersions().stream()
                            .filter(t -> appMarketDownloadDTO.getAppVersionIds().contains(t.getId()))
                            .collect(Collectors.toCollection(ArrayList::new))
            );
            String appMarketJson = gson.toJson(applicationReleasingDTO);
            FileUtil.saveDataToFile(destpath, applicationReleasingDTO.getCode() + JSON_FILE, appMarketJson);
            //下载chart taz包
            getChart(images, appMarketDownloadDTO, destpath, applicationE, projectE, organization);
            StringBuilder stringBuilder = new StringBuilder();
            for (String image : images) {
                stringBuilder.append(image);
                stringBuilder.append(System.getProperty("line.separator"));
            }
            InputStream inputStream = this.getClass().getResourceAsStream("/shell/push_image.sh");
            FileUtil.saveDataToFile(fileName, PUSH_IAMGES, FileUtil.replaceReturnString(inputStream, null));
            FileUtil.saveDataToFile(fileName, IMAGES, stringBuilder.toString());
            FileUtil.moveFiles(CHARTS, fileName);
        }
        try (FileOutputStream outputStream = new FileOutputStream(fileName + ".zip")) {
            FileUtil.toZip(fileName, outputStream, true);
            FileUtil.deleteDirectory(new File(CHARTS));
            FileUtil.deleteDirectory(new File(fileName));
        } catch (IOException e) {
            throw new CommonException(e.getMessage(), e);
        }
    }

    private void getChart(List<String> images, AppMarketDownloadDTO appMarketDownloadDTO, String destpath, ApplicationE applicationE, ProjectE projectE, Organization organization) {
        appMarketDownloadDTO.getAppVersionIds().forEach(appVersionId -> {

            ApplicationVersionE applicationVersionE = applicationVersionRepository.query(appVersionId);
            images.add(applicationVersionE.getImage());
            chartUtil.downloadChart(applicationVersionE, organization, projectE, applicationE, destpath);
        });
    }

    private void createVersion(AppMarketVersionDTO appVersion,
                               String organizationCode,
                               String projectCode,
                               String appCode,
                               Long appId,
                               File[] appFiles,
                               Boolean isVersionPublish) {
        ApplicationVersionE applicationVersionE = new ApplicationVersionE();
        String image = String.format("%s%s%s%s%s%s%s%s%s", harborConfigurationProperties.getBaseUrl(),
                FILE_SEPARATOR,
                organizationCode,
                "-",
                projectCode,
                FILE_SEPARATOR,
                appCode,
                ":",
                appVersion.getVersion()
        );
        applicationVersionE.setImage(image);
        helmUrl = helmUrl.endsWith("/") ? helmUrl : helmUrl + "/";
        applicationVersionE.setRepository(String.format("%s%s%s%s%s",
                helmUrl,
                organizationCode,
                FILE_SEPARATOR,
                projectCode,
                FILE_SEPARATOR));
        applicationVersionE.setVersion(appVersion.getVersion());
        applicationVersionE.initApplicationEById(appId);
        String tazName = String.format("%s%s%s%s",
                appCode,
                "-",
                appVersion.getVersion(),
                ".tgz"
        );
        List<File> tgzVersions = Arrays.stream(appFiles).parallel()
                .filter(k -> k.getName().equals(tazName))
                .collect(Collectors.toCollection(ArrayList::new));
        if (!tgzVersions.isEmpty()) {
            ApplicationVersionValueE applicationVersionValueE = new ApplicationVersionValueE();
            try {
                FileUtil.unTarGZ(tgzVersions.get(0).getAbsolutePath(), appCode);
                File valueYaml = FileUtil.queryFileFromFiles(new File(appCode), "values.yaml");
                if (valueYaml == null) {
                    throw new CommonException("error.version.values.notExist");
                }
                applicationVersionValueE.setValue(FileUtil.replaceReturnString(new FileInputStream(valueYaml), null));

                applicationVersionE.initApplicationVersionValueE(applicationVersionValueRepository
                        .create(applicationVersionValueE).getId());
            } catch (Exception e) {
                throw new CommonException("error.version.insert");
            }
            applicationVersionE.initApplicationVersionReadmeV(FileUtil.getReadme(appCode));
            ApplicationVersionE version = applicationVersionRepository
                    .queryByAppAndVersion(appId, appVersion.getVersion());

            if (isVersionPublish) {
                applicationVersionE.setIsPublish(1L);
            } else {
                applicationVersionE.setIsPublish(version == null ? null : version.getIsPublish());
            }
            if (version == null) {
                applicationVersionRepository.create(applicationVersionE);
            } else {
                applicationVersionE.setId(version.getId());
                applicationVersionRepository.updateVersion(applicationVersionE);
            }
            String classPath = String.format("Charts%s%s%s%s",
                    FILE_SEPARATOR,
                    organizationCode,
                    FILE_SEPARATOR,
                    projectCode);
            FileUtil.copyFile(tgzVersions.get(0).getAbsolutePath(), classPath);
            //上传tgz包到chart仓库
            chartUtil.uploadChart(organizationCode, projectCode, tgzVersions.get(0));
            FileUtil.deleteDirectory(new File(appCode));
        }
    }


    private Long createOrUpdateApp(ApplicationE applicationE, String appCode, Long projectId) {
        applicationE.setCode(appCode);
        applicationE.initProjectE(projectId);
        Long appId;
        Boolean appCodeExist = false;
        try {
            applicationRepository.checkCode(applicationE);
        } catch (Exception e) {
            logger.info(e.getMessage());
            appCodeExist = true;
        }
        if (!appCodeExist) {
            applicationE.setActive(true);
            applicationE.setSynchro(true);
            applicationE.setToken(GenerateUUID.generateUUID());
            appId = applicationRepository.create(applicationE).getId();
        } else {
            ApplicationE existApplication = applicationRepository.queryByCode(appCode, projectId);
            appId = existApplication.getId();
            applicationE.setId(appId);
            applicationRepository.update(applicationE);
        }
        return appId;
    }

    private Boolean checkAppCanPub(Long appId) {
        try {
            return appShareRepository.checkCanPub(appId);
        } catch (Exception e) {
            return false;
        }
    }

    private void releaseApp(Boolean isPublic,
                            ApplicationReleasingDTO applicationReleasingDTO, Long appId) {
        if (isPublic != null) {
            Boolean canPub = checkAppCanPub(appId);
            if (canPub) {
                DevopsAppShareE applicationMarketE = new DevopsAppShareE();
                applicationMarketE.initApplicationEById(appId);
                applicationMarketE.setPublishLevel(isPublic ? PUBLIC : ORGANIZATION);
                applicationMarketE.setActive(true);
                applicationMarketE.setContributor(applicationReleasingDTO.getContributor());
                applicationMarketE.setDescription(applicationReleasingDTO.getDescription());
                applicationMarketE.setCategory(applicationReleasingDTO.getCategory());
                appShareRepository.create(applicationMarketE);
            }
        }
    }
}
