package io.choerodon.devops.infra.feign.fallback;

import io.choerodon.core.exception.CommonException;
import io.choerodon.devops.api.vo.kubernetes.ProjectCategoryEDTO;
import io.choerodon.devops.infra.dto.iam.ProjectCategoryDTO;
import io.choerodon.devops.infra.feign.OrgServiceClient;

import com.github.pagehelper.PageInfo;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * Creator: ChangpingShi0213@gmail.com
 * Date:  15:53 2019/6/24
 * Description:
 */
@Component
public class OrgServiceClientFallBack implements OrgServiceClient {
    @Override
    public ResponseEntity<ProjectCategoryEDTO> createProjectCategory(Long organizationId, ProjectCategoryEDTO createDTO) {
        throw new CommonException("error.project.category.create");
    }

    @Override
    public ResponseEntity<PageInfo<ProjectCategoryDTO>> getProjectCategoryList(Long organizationId, int page, int size, String param) {
        throw new CommonException("error.project.category.list");
    }
}