package io.choerodon.devops.infra.persistence.impl;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.choerodon.core.exception.CommonException;
import io.choerodon.devops.domain.application.repository.PipelineUserRelRepository;
import io.choerodon.devops.infra.dto.PipelineUserRelationshipDTO;
import io.choerodon.devops.infra.mapper.PipelineUserRelMapper;

/**
 * Creator: ChangpingShi0213@gmail.com
 * Date:  17:10 2019/4/8
 * Description:
 */
@Component
public class PipelineUserRelRepositoryImpl implements PipelineUserRelRepository {
    @Autowired
    private PipelineUserRelMapper userRelMapper;

    @Override
    public void baseCreate(PipelineUserRelationshipDTO pipelineUserRelationShipDTO) {
        if (userRelMapper.insert(pipelineUserRelationShipDTO) != 1) {
            throw new CommonException("error.insert.pipeline.user");
        }
    }

    @Override
    public List<PipelineUserRelationshipDTO> baseListByOptions(Long pipelineId, Long stageId, Long taskId) {
        PipelineUserRelationshipDTO pipelineUserRelationShipDTO = new PipelineUserRelationshipDTO(pipelineId, stageId, taskId);
        return userRelMapper.select(pipelineUserRelationShipDTO);
    }

    @Override
    public void baseDelete(PipelineUserRelationshipDTO pipelineUserRelationShipDTO) {
        userRelMapper.delete(pipelineUserRelationShipDTO);
    }
}
