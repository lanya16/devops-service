package io.choerodon.devops.domain.application.repository;

import io.choerodon.devops.infra.dto.PipelineUserRelationshipDTO;

import java.util.List;

/**
 * Creator: ChangpingShi0213@gmail.com
 * Date:  17:08 2019/4/8
 * Description:
 */
public interface PipelineUserRelRepository {
    void baseCreate(PipelineUserRelationshipDTO pipelineUserRelationShipDTO);

    List<PipelineUserRelationshipDTO> baseListByOptions(Long pipelineId, Long stageId, Long taskId);

    void baseDelete(PipelineUserRelationshipDTO pipelineUserRelationShipDTO);
}
