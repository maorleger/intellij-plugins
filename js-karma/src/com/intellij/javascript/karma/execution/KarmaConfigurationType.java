package com.intellij.javascript.karma.execution;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationTypeBase;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.project.Project;
import icons.JSKarmaIcons;
import org.jetbrains.annotations.NotNull;

/**
 * @author Sergey Simonchik
 */
public class KarmaConfigurationType extends ConfigurationTypeBase {

  public KarmaConfigurationType() {
    super("JavaScriptTestRunnerKarma", "Karma", "Karma", JSKarmaIcons.Karma);
    addFactory(new ConfigurationFactory(this) {
      @Override
      public RunConfiguration createTemplateConfiguration(Project project) {
        return new KarmaRunConfiguration(project, this, "Karma");
      }
    });
  }

  @NotNull
  public static KarmaConfigurationType getInstance() {
    return ConfigurationTypeUtil.findConfigurationType(KarmaConfigurationType.class);
  }

}
