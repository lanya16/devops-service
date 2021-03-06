import React, { createContext, useContext, useMemo, useEffect } from 'react';
import { inject } from 'mobx-react';
import { injectIntl } from 'react-intl';
import { DataSet } from 'choerodon-ui/pro';
import useStore from './useStore';
import useEditBlockStore from './useEditBlockStore';
import useDetailStore from './useDetailStore';
import TreeDataSet from './TreeDataSet';

const Store = createContext();

export function usePipelineManageStore() {
  return useContext(Store);
}

export const StoreProvider = injectIntl(inject('AppState')((props) => {
  const {
    AppState: { currentMenuType: { projectId } },
    children,
  } = props;

  const mainStore = useStore();
  const editBlockStore = useEditBlockStore();
  const detailStore = useDetailStore();
  const treeDs = useMemo(() => new DataSet(TreeDataSet({ projectId, mainStore })), [projectId]);

  useEffect(() => {
    const { key } = mainStore.getSelectedMenu;
    if (key) {
      const selectedRecord = treeDs.find((record) => record.get('key') === key);
      if (!selectedRecord) {
        const newRecord = treeDs.records[0];
        newRecord.isSelected = true;
        mainStore.setSelectedMenu(newRecord.toData());
      }
    }
  }, [treeDs.records]);

  const value = {
    ...props,
    prefixCls: 'c7ncd-pipelineManage',
    intlPrefix: 'c7ncd.pipelineManage',
    permissions: [
      'devops-service.devops-ci-pipeline-record.pagingPipelineRecord',
      'devops-service.devops-ci-pipeline-record.queryPipelineRecordDetails',
      'devops-service.devops-ci-pipeline.listByProjectIdAndAppName',
      'devops-service.devops-ci-pipeline.create',
      'devops-service.devops-ci-pipeline.query',
      'devops-service.devops-ci-pipeline.update',
      'devops-service.devops-ci-pipeline.deletePipeline',
      'devops-service.devops-ci-pipeline.disablePipeline',
      'devops-service.devops-ci-pipeline.enablePipeline',
      'devops-service.project-pipeline.create',
      'devops-service.project-pipeline.cancel',
      'devops-service.project-pipeline.retry',
    ],
    mainStore,
    treeDs,
    detailStore,
    editBlockStore,
    projectId,
  };

  return (
    <Store.Provider value={value}>
      {children}
    </Store.Provider>
  );
}));
