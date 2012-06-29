package com.google.jstestdriver.idea.assertFramework.support;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.jstestdriver.idea.assertFramework.library.JsLibraryHelper;
import com.intellij.lang.javascript.library.JSLibraryManager;
import com.intellij.lang.javascript.library.JSLibraryMappings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.util.ui.UIUtil;
import com.intellij.webcore.libraries.ScriptingLibraryMappings;
import com.intellij.webcore.libraries.ScriptingLibraryModel;
import com.intellij.webcore.libraries.ui.ModuleScopeSelectorComponent;
import com.intellij.webcore.ui.SwingHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * @author Sergey Simonchik
 */
public class ChooseScopeAndCreateLibraryDialog extends DialogWrapper {

  private static final Logger LOG = Logger.getInstance(ChooseScopeAndCreateLibraryDialog.class);

  private final Project myProject;
  private final String myLibraryName;
  private final ImmutableList<VirtualFile> myLibraryFiles;
  private final ModuleScopeSelectorComponent myModuleSelector;
  private final JTextField myLibraryNameTextField;
  private final JPanel myComponent;

  public ChooseScopeAndCreateLibraryDialog(@NotNull Project project,
                                           @NotNull String desiredLibraryName,
                                           @NotNull List<VirtualFile> libraryFiles,
                                           @Nullable VirtualFile requestor,
                                           boolean warnAboutOutsideCode) {
    super(project);
    myProject = project;
    JsLibraryHelper jsLibraryHelper = new JsLibraryHelper(myProject);
    myLibraryName = jsLibraryHelper.findAvailableJsLibraryName(desiredLibraryName);
    myLibraryFiles = ImmutableList.copyOf(libraryFiles);
    setTitle("Code Assistance For " + desiredLibraryName);

    myModuleSelector = new ModuleScopeSelectorComponent(project, requestor);
    myLibraryNameTextField = new JTextField(myLibraryName);

    myComponent = SwingHelper.newLeftAlignedVerticalPanel(
      createDescription(warnAboutOutsideCode),
      Box.createVerticalStrut(10),
      createLibraryNamePanel(),
      Box.createVerticalStrut(5),
      createCompletionPanel()
    );
    super.init();
  }

  private static JComponent createDescription(boolean warnAboutOutsideCode) {
    List<Component> components = Lists.newArrayList();
    if (warnAboutOutsideCode) {
      JLabel warnLabel = new JLabel(UIUtil.getBalloonWarningIcon());
      warnLabel.setText(" Added files have been placed outside of the project.");
      components.add(warnLabel);
      components.add(Box.createVerticalStrut(10));
    }
    components.add(new JLabel("JavaScript library will be created to provide code assistance."));
    return SwingHelper.newLeftAlignedVerticalPanel(components);
  }

  @NotNull
  private JPanel createCompletionPanel() {
    JPanel completionPanel = new JPanel(new BorderLayout());
    completionPanel.add(myModuleSelector.getComponent(), BorderLayout.CENTER);
    completionPanel.setBorder(IdeBorderFactory.createTitledBorder("Code completion"));
    return completionPanel;
  }

  @NotNull
  private Component createLibraryNamePanel() {
    JPanel libraryNamePanel = new JPanel(new GridBagLayout());
    libraryNamePanel.add(new JLabel("Library name:"), new GridBagConstraints(
      0, 0,
      1, 1,
      0.0, 0.0,
      GridBagConstraints.WEST,
      GridBagConstraints.NONE,
      new Insets(0, 0, 0, 5),
      0, 0
    ));
    libraryNamePanel.add(myLibraryNameTextField, new GridBagConstraints(
      1, 0,
      1, 1,
      1.0, 0.0,
      GridBagConstraints.WEST,
      GridBagConstraints.HORIZONTAL,
      new Insets(0, 0, 0, 0),
      0, 0
    ));
    return libraryNamePanel;
  }

  @Override
  protected JComponent createCenterPanel() {
    return myComponent;
  }

  @Override
  public ValidationInfo doValidate() {
    JsLibraryHelper jsLibraryHelper = new JsLibraryHelper(myProject);
    ScriptingLibraryModel libraryModel = jsLibraryHelper.getScriptingLibraryModel(myLibraryNameTextField.getText());
    if (libraryModel != null) {
      return new ValidationInfo("Library with such name already exists", myLibraryNameTextField);
    }
    return null;
  }

  @Override
  protected void doOKAction() {
    createLibraryAndAssociate();
    super.doOKAction();
  }

  private void createLibraryAndAssociate() {
    JsLibraryHelper jsLibraryHelper = new JsLibraryHelper(myProject);
    String libraryName = myLibraryNameTextField.getText();
    final ScriptingLibraryModel libraryModel = jsLibraryHelper.createJsLibrary(libraryName, myLibraryFiles);

    String dialogTitle = "Adding " + myLibraryName;
    if (libraryModel == null) {
      Messages.showErrorDialog("Unable to create '" + libraryName + "' JavaScript library", dialogTitle);
      return;
    }

    boolean success = ApplicationManager.getApplication().runWriteAction(new Computable<Boolean>() {
      @Override
      @NotNull
      public Boolean compute() {
        try {
          ScriptingLibraryMappings libraryMappings = ServiceManager.getService(myProject, JSLibraryMappings.class);
          if (myModuleSelector.isProjectAssociationAllowed()) {
            libraryMappings.associateWithProject(libraryModel.getName());
            LOG.info("Library '" + libraryModel.getName() + "' has been successfully associated with the project");
          }
          else {
            for (Module module : myModuleSelector.getSelectedModules()) {
              ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
              VirtualFile[] roots = moduleRootManager.getContentRoots();
              for (VirtualFile root : roots) {
                libraryMappings.associate(root, libraryModel.getName(), false);
                LOG.info("Library '" + libraryModel.getName() + "' has been associated with " + root);
              }
            }
          }
          JSLibraryManager libraryManager = ServiceManager.getService(myProject, JSLibraryManager.class);
          libraryManager.commitChanges();
          return true;
        } catch (Exception ex) {
          LOG.error(ex);
          return false;
        }
      }
    });
    if (!success) {
      Messages.showErrorDialog("Unable to associate '" + libraryName + "' JavaScript library", dialogTitle);
    }
  }
}