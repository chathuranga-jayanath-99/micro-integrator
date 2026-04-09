/*
 * Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.micro.integrator.ntask.coordination.task.store.cleaner;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.micro.integrator.coordination.ClusterCoordinator;
import org.wso2.micro.integrator.ntask.coordination.TaskCoordinationException;
import org.wso2.micro.integrator.ntask.coordination.task.CoordinatedTask;
import org.wso2.micro.integrator.ntask.coordination.task.store.TaskStore;
import org.wso2.micro.integrator.ntask.core.impl.standalone.ScheduledTaskManager;
import org.wso2.micro.integrator.ntask.core.internal.DataHolder;
import org.wso2.micro.integrator.ntask.core.internal.TaskHandlingConfigUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The class which is responsible for cleaning the task store. This will remove the tasks if they are invalid and
 * will remove the node assignments if they are no more present in the cluster.
 */
public class TaskStoreCleaner {

    private static final Log LOG = LogFactory.getLog(TaskStoreCleaner.class);
    private static final long INVALID_NODE_CLEANUP_WAIT_POLL_INTERVAL_MILLIS = 200L;
    private static final long DELETE_GUARD_SETTLE_BUFFER_MILLIS = 5000L;

    private DataHolder dataHolder = DataHolder.getInstance();
    private ClusterCoordinator clusterCoordinator = dataHolder.getClusterCoordinator();
    private TaskStore taskStore;
    private ScheduledTaskManager taskManager;
    private final boolean taskDeleteBarrierEnabled;

    /**
     * Constructor.
     *
     * @param taskStore - Task database.
     */
    public TaskStoreCleaner(ScheduledTaskManager taskManager, TaskStore taskStore) {
        this.taskManager = taskManager;
        this.taskStore = taskStore;
        this.taskDeleteBarrierEnabled = TaskHandlingConfigUtils.isTaskDeleteBarrierEnabled();
    }

    /**
     * Cleans the task store. Removes the invalid tasks and invalid nodes ( nodes that are no more in cluster ).
     *
     * @throws TaskCoordinationException When something goes wrong while connecting to the store.
     */
    public void clean() throws TaskCoordinationException {

        LOG.debug("Starting task store cleaning.");
        List<CoordinatedTask> allTasks = taskStore.getAllTaskNames();
        List<String> allNodesAvailableInCluster = clusterCoordinator.getAllNodeIds();
        if (allTasks.isEmpty()) {
            LOG.debug("No tasks found in task database.");
            if (!taskDeleteBarrierEnabled) {
                LOG.debug("Completed task store cleaning.");
                return;
            }
        } else {
            removeInvalidTasksFromStore(allTasks, allNodesAvailableInCluster);
            validateDestinedNodeAndUpdateStore(allNodesAvailableInCluster);
        }
        if (taskDeleteBarrierEnabled) {
            recoverExpiredOrAbandonedDeleteBarriers(allNodesAvailableInCluster);
        }
        LOG.debug("Completed task store cleaning.");
    }

    /**
     * Checks whether the destined node is valid and remove it if it is not.
     *
     * @param allNodesAvailableInCluster - all available nodes in the cluster
     * @throws TaskCoordinationException - When something goes wrong while updating tasks.
     */
    private void validateDestinedNodeAndUpdateStore(List<String> allNodesAvailableInCluster)
            throws TaskCoordinationException {

        List<CoordinatedTask> assignedIncompleteTasks = taskStore.getAllAssignedIncompleteTasks();

        if (allNodesAvailableInCluster.isEmpty()) {
            LOG.warn("No nodes are registered to the cluster successfully yet.");
            return;
        }
        List<String> tasksToBeUpdated = new ArrayList<>();
        assignedIncompleteTasks.forEach(task -> {
            // check whether the node assigned is still valid
            String nodeId = task.getDestinedNodeId();
            if (!allNodesAvailableInCluster.contains(nodeId)) {
                String taskName = task.getTaskName();
                if (LOG.isDebugEnabled()) {
                    LOG.debug("The node [" + nodeId + "] of task [" + taskName + "] is not found in cluster"
                                      + ". Hence the node assignment will be removed.");
                }
                LOG.info("The node [" + nodeId + "] of task [" + taskName + "] is not found in cluster."
                        + " Hence the node assignment will be removed.");
                tasksToBeUpdated.add(taskName);
            }
        });
        if (taskDeleteBarrierEnabled && !tasksToBeUpdated.isEmpty()) {
            long delayMillis = getGuardBasedInvalidNodeCleanupDelayMillis();
            if (delayMillis > 0) {
                LOG.info("Delaying invalid-node unassignment by [" + delayMillis
                        + "] ms based on latest delete guard update.");
                waitBeforeInvalidNodeCleanup(delayMillis);
            } else {
                LOG.info("No guard-based delay applied before invalid-node unassignment.");
            }
        }
        taskStore.unAssignAndUpdateState(tasksToBeUpdated);
    }

    /**
     * From the list of tasks provided removes the invalid tasks entries in the store ( i.e tasks that are not
     * deployed as coordinated task.
     *
     * @param tasksList - The list of tasks to be checked.
     * @param allNodesAvailableInCluster - all available nodes in the cluster
     * @throws TaskCoordinationException - When something goes wrong while updating the store.
     */
    private void removeInvalidTasksFromStore(List<CoordinatedTask> tasksList, List<String> allNodesAvailableInCluster)
            throws TaskCoordinationException {

        List<String> deployedCoordinatedTasks = taskManager.getAllCoordinatedTasksDeployed();
        if (LOG.isDebugEnabled()) {
            LOG.debug("Following list of tasks are found deployed coordinated task list.");
            deployedCoordinatedTasks.forEach(LOG::debug);
        }
        // We first add to list and then to the store  while deploying. So all the tasks retrieved from the store
        // which has valid node ids should be in the list, if not they are invalid entries.
        tasksList.removeIf(task -> allNodesAvailableInCluster.contains(task.getDestinedNodeId()));
        tasksList.removeIf(task -> deployedCoordinatedTasks.contains(task.getTaskName()));
        taskStore.deleteTasks(tasksList.stream().map(CoordinatedTask::getTaskName).collect(Collectors.toList()));
        if (LOG.isDebugEnabled()) {
            tasksList.forEach(removedTask -> LOG.debug("Removed invalid task :" + removedTask));
        }
    }

    /**
     * Recovers delete barriers owned by dead nodes or expired by deadline.
     *
     * @param allNodesAvailableInCluster currently live nodes
     * @throws TaskCoordinationException when recovery query execution fails
     */
    private void recoverExpiredOrAbandonedDeleteBarriers(List<String> allNodesAvailableInCluster)
            throws TaskCoordinationException {
        List<String> recoveredTaskNames = taskStore.recoverExpiredOrAbandonedDeleteBarriers(allNodesAvailableInCluster,
                System.currentTimeMillis());
        if (recoveredTaskNames.isEmpty()) {
            return;
        }
        List<String> deployedCoordinatedTasks = taskManager.getAllCoordinatedTasksDeployed();
        int reAdded = 0;
        for (String taskName : recoveredTaskNames) {
            if (!deployedCoordinatedTasks.contains(taskName)) {
                continue;
            }
            taskStore.addTaskIfNotExist(taskName);
            reAdded++;
            LOG.info("Recovery flow re added coordinated task row for task [" + taskName
                    + "] after delete barrier recovery.");
        }
        LOG.info("Recovered [" + recoveredTaskNames.size() + "] expired or abandoned task delete barrier(s).");
        LOG.info("Recovery flow re added [" + reAdded + "] coordinated task row(s) after barrier recovery.");
    }

    /**
     * Computes wait time for invalid node unassignment based on latest delete guard update in DB.
     *
     * @return delay in millis before invalid-node unassignment
     * @throws TaskCoordinationException when DB operations fail
     */
    private long getGuardBasedInvalidNodeCleanupDelayMillis() throws TaskCoordinationException {
        long latestGuardUpdatedAt = taskStore.getLatestDeleteGuardUpdatedAt();
        if (latestGuardUpdatedAt <= 0) {
            return 0;
        }
        long waitUntil = latestGuardUpdatedAt + clusterCoordinator.getHeartbeatMaxRetryInterval()
                + DELETE_GUARD_SETTLE_BUFFER_MILLIS;
        long remaining = waitUntil - System.currentTimeMillis();
        return Math.max(remaining, 0);
    }

    /**
     * Waits until the computed invalid-node cleanup delay elapses, while honoring interruption.
     *
     * @param delayMillis delay in millis
     */
    private void waitBeforeInvalidNodeCleanup(long delayMillis) {
        long waitUntil = System.currentTimeMillis() + delayMillis;
        while (true) {
            long remaining = waitUntil - System.currentTimeMillis();
            if (remaining <= 0) {
                return;
            }
            try {
                Thread.sleep(Math.min(remaining, INVALID_NODE_CLEANUP_WAIT_POLL_INTERVAL_MILLIS));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.warn("Interrupted while waiting to clean invalid-node task assignments.");
                return;
            }
        }
    }

}
