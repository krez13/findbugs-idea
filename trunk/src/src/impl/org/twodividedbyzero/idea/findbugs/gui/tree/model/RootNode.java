package org.twodividedbyzero.idea.findbugs.gui.tree.model;

import edu.umd.cs.findbugs.BugInstance;
import org.jetbrains.annotations.Nullable;
import org.twodividedbyzero.idea.findbugs.gui.tree.NodeVisitor;
import org.twodividedbyzero.idea.findbugs.gui.tree.RecurseNodeVisitor;
import org.twodividedbyzero.idea.findbugs.gui.tree.RecurseNodeVisitor.RecurseVisitCriteria;
import org.twodividedbyzero.idea.findbugs.gui.tree.view.MaskIcon;
import org.twodividedbyzero.idea.findbugs.resources.ResourcesLoader;

import javax.swing.Icon;
import javax.swing.tree.TreeNode;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;


/**
 * $Date$
 *
 * @version $Revision$
 */
public class RootNode extends AbstractTreeNode<VisitableTreeNode> implements VisitableTreeNode {

	private int _bugCount;
	private int _classesCount;
	private final List<VisitableTreeNode> _childs;

	private static final Icon _expandedIcon = new MaskIcon(ResourcesLoader.findIcon("/general/ijLogo.png", RootNode.class), Color.BLACK);
	private static final Icon _collapsedIcon = _expandedIcon;
	private final RecurseNodeVisitor<RootNode> _recurseNodeVisitor = new RecurseNodeVisitor<RootNode>(this);


	public RootNode(final String simpleName) {

		//noinspection AssignmentToNull
		setParent(null);
		_childs = new ArrayList<VisitableTreeNode>();
		_simpleName = simpleName;
		_bugCount = -1;
		_classesCount = 0;

		setCollapsedIcon(_collapsedIcon);
		setExpandedIcon(_expandedIcon);
	}


	public void setSimpleName(final String simpleName) {
		_simpleName = simpleName;
	}


	public int getBugCount() {
		return _bugCount;
	}


	public void setBugCount(final int bugCount) {
		_bugCount = bugCount;
	}


	public void incrementMemberCount() {
		++_memberCount;
	}


	public int getClassesCount() {
		return _classesCount;
	}


	/**
	 * Perfomrs a deep search. Get child BugInstanceGroupNode by BugInstance group name.
	 *
	 * @param groupName the group name to search for
	 * @param depth
	 * @return the BugInstanceGroupNode
	 * @deprecated use {@link RootNode#findChildNode(edu.umd.cs.findbugs.BugInstance, int, String)}
	 */
	@Deprecated
	@Nullable
	public BugInstanceGroupNode getChildByGroupName(final String groupName, final int depth) {
		BugInstanceGroupNode resultNode = null;

		for (final TreeNode node : _childs) {
			if (node instanceof BugInstanceGroupNode) {
				final BugInstanceGroupNode groupNode = (BugInstanceGroupNode) node;
				if (groupName.equals(groupNode.getGroupName()) && depth == groupNode.getDepth()) {
					resultNode = groupNode;
				} else {
					resultNode = groupNode.getChildByGroupName(groupName, depth);
				}

				if (resultNode != null) {
					break;
				}

			}
		}

		return resultNode;
	}


	/**
	 * Perfomrs a deep search. Get child BugInstanceGroupNode by BugInstance object.
	 *
	 * @param bugInstance the findbugs buginstance to search for
	 * @param depth	   the machting depth to search for
	 * @return the BugInstanceGroupNode
	 * @deprecated use {@link RootNode#findChildNode(edu.umd.cs.findbugs.BugInstance, int, String)}
	 */
	@Deprecated
	@Nullable
	public BugInstanceGroupNode getChildByBugInstance(final BugInstance bugInstance, final int depth) {
		BugInstanceGroupNode resultNode = null;

		for (final TreeNode node : _childs) {
			if (node instanceof BugInstanceGroupNode) {
				final BugInstanceGroupNode groupNode = (BugInstanceGroupNode) node;
				if (bugInstance.equals(groupNode.getBugInstance()) && depth == groupNode.getDepth()) {
					resultNode = groupNode;
				} else {
					resultNode = groupNode.getChildByBugInstance(bugInstance, depth);
				}

				if (resultNode != null) {
					break;
				}
			}
		}

		return resultNode;
	}


	@Nullable
	public BugInstanceGroupNode findChildNode(final BugInstance bugInstance, final int depth, final String groupName) {
		final RecurseVisitCriteria criteria = new RecurseVisitCriteria(bugInstance, depth, groupName);
		return _recurseNodeVisitor.findChildNode(criteria);
	}


	public List<BugInstance> getChildBugInstances() {
		final List<BugInstance> list = new ArrayList<BugInstance>();

		for (final TreeNode child : _childs) {
			if (child instanceof BugInstanceGroupNode) {
				final BugInstance bugInstance = ((BugInstanceGroupNode) child).getBugInstance();
				list.add(bugInstance);
			}
		}

		return list;
	}


	public List<BugInstance> getAllChildBugInstances() {
		final List<BugInstance> list = new ArrayList<BugInstance>();

		for (final TreeNode child : _childs) {
			if (child instanceof BugInstanceGroupNode) {
				final BugInstanceGroupNode node = (BugInstanceGroupNode) child;
				list.add(node.getBugInstance());
				final List<BugInstance> bugInstances = node.getAllChildBugInstances();
				list.addAll(list.size(), bugInstances);
			}
		}

		return list;
	}


	public void setClassesCount(final int classesCount) {
		_classesCount = classesCount;
	}


	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder();
		sb.append("RootNode");
		sb.append("{_bugCount=").append(_bugCount);
		sb.append(", _classesCount=").append(_classesCount);
		sb.append(", _childs=").append(_childs);
		sb.append(", _recurseNodeVisitor=").append(_recurseNodeVisitor);
		sb.append('}');
		return sb.toString();
	}


	@Override
	public void accept(final NodeVisitor visitor) {
		//visitor.visitGroupNode(this);
	}


	@Override
	public List<VisitableTreeNode> getChildsList() {
		return _childs;
	}


	public RootNode getTreeNode() {
		return this;
	}


	public boolean getAllowsChildren() {
		return true;
	}


	public boolean isLeaf() {
		return _childs.isEmpty();
	}
}