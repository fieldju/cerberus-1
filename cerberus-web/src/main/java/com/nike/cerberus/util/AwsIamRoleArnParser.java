/*
 * Copyright (c) 2020 Nike, inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nike.cerberus.util;

import com.nike.backstopper.exception.ApiException;
import com.nike.cerberus.domain.DomainConstants;
import com.nike.cerberus.error.DefaultApiError;
import com.nike.cerberus.error.InvalidIamRoleArnApiError;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Utility class for concatenating and parsing AWS IAM role ARNs. */
@Component
public class AwsIamRoleArnParser {
  private final boolean awsChinaEnabled;
  private final boolean awsGlobalEnabled;

  public AwsIamRoleArnParser(
      @Value("${cerberus.partitions.awsGlobal.enabled}") boolean awsGlobalEnabled,
      @Value("${cerberus.partitions.awsChina.enabled}") boolean awsChinaEnabled) {
    this.awsGlobalEnabled = awsGlobalEnabled;
    this.awsChinaEnabled = awsChinaEnabled;
  }

  /**
   * Gets account ID from a 'role' ARN
   *
   * @param roleArn - Role ARN to parse
   * @return - Account ID
   */
  public String getAccountId(final String roleArn) {
    return getNamedGroupFromRegexPattern(
        DomainConstants.IAM_ROLE_ARN_PATTERN, "accountId", roleArn);
  }

  /**
   * Gets role name form a 'role' ARN
   *
   * @param roleArn - Role ARN to parse
   * @return
   */
  public String getRoleName(final String roleArn) {
    return getNamedGroupFromRegexPattern(DomainConstants.IAM_ROLE_ARN_PATTERN, "roleName", roleArn);
  }

  /**
   * Returns true if the ARN is in format 'arn:aws:iam::000000000:role/example' and false if not
   *
   * @param arn - ARN to test
   * @return - True if is 'role' ARN, False if not
   */
  public boolean isRoleArn(final String arn) {

    final Matcher iamRoleArnMatcher = DomainConstants.IAM_ROLE_ARN_PATTERN.matcher(arn);

    return iamRoleArnMatcher.find();
  }

  /**
   * Returns true if the ARN is in format 'arn:aws:sts::000000000:assumed-role/example/role-session'
   * and false if not
   *
   * @param arn - ARN to test
   * @return - True if is 'role' ARN, False if not
   */
  public boolean isAssumedRoleArn(final String arn) {

    final Matcher iamAssumedRoleArnMatcher =
        DomainConstants.IAM_ASSUMED_ROLE_ARN_PATTERN.matcher(arn);

    return iamAssumedRoleArnMatcher.find();
  }

  /**
   * Returns true if the ARN is in format 'arn:aws:iam::000000000:root' and false if not
   *
   * @param arn - ARN to test
   * @return - True if is 'role' ARN, False if not
   */
  public boolean isAccountRootArn(final String arn) {

    final Matcher accountRootArnMatcher = DomainConstants.AWS_ACCOUNT_ROOT_ARN_PATTERN.matcher(arn);

    return accountRootArnMatcher.find();
  }

  public boolean isArnThatCanGoInKeyPolicy(final String arn) {
    final Matcher arnMatcher = DomainConstants.IAM_PRINCIPAL_ARN_PATTERN_ALLOWED.matcher(arn);
    final Matcher rootArnMatcher = DomainConstants.AWS_ACCOUNT_ROOT_ARN_PATTERN.matcher(arn);

    return arnMatcher.find() || rootArnMatcher.find();
  }

  /**
   * Converts a principal ARN (e.g. 'arn:aws:iam::0000000000:instance-profile/example') to a role
   * ARN, (i.e. 'arn:aws:iam::000000000:role/example')
   *
   * @param principalArn - Principal ARN to convert
   * @return - Role ARN
   */
  public String convertPrincipalArnToRoleArn(final String principalArn) {

    if (isRoleArn(principalArn)) {
      return principalArn;
    }

    final boolean isAssumedRole =
        DomainConstants.GENERIC_ASSUMED_ROLE_PATTERN.matcher(principalArn).find();
    final Pattern patternToMatch =
        isAssumedRole
            ? DomainConstants.IAM_ASSUMED_ROLE_ARN_PATTERN
            : DomainConstants.IAM_PRINCIPAL_ARN_PATTERN_ROLE_GENERATION;

    final String accountId =
        getNamedGroupFromRegexPattern(patternToMatch, "accountId", principalArn);
    final String roleName = getNamedGroupFromRegexPattern(patternToMatch, "roleName", principalArn);
    final String partition =
        getNamedGroupFromRegexPattern(patternToMatch, "partition", principalArn);

    return String.format(DomainConstants.AWS_IAM_ROLE_ARN_TEMPLATE, partition, accountId, roleName);
  }

  public String convertPrincipalArnToRootArn(final String principalArn) {

    if (isAccountRootArn(principalArn)) {
      return principalArn;
    }

    final String accountId =
        getNamedGroupFromRegexPattern(
            DomainConstants.IAM_PRINCIPAL_ARN_PATTERN_ALLOWED, "accountId", principalArn);

    final String partition =
        getNamedGroupFromRegexPattern(
            DomainConstants.IAM_PRINCIPAL_ARN_PATTERN_ALLOWED, "partition", principalArn);

    return String.format("arn:%s:iam::%s:root", partition, accountId);
  }

  /**
   * Strip out a description from the supplied ARN, e.g. "123456789/role-name" or the empty string
   */
  public String stripOutDescription(final String principalArn) {
    Matcher matcher =
        DomainConstants.IAM_PRINCIPAL_ARN_PATTERN_ROLE_GENERATION.matcher(principalArn);
    if (matcher.find()) {
      return matcher.group("accountId") + "/" + matcher.group("roleName");
    } else {
      // never return null since this is just for descriptive purposes
      return "";
    }
  }

  /**
   * Checks if the partition of an IAM principal ARN is enabled
   *
   * @param iamPrincipalArn The IAM principal ARN to be checked
   * @throws ApiException Throws an exception if the partition of the IAM principal isn't enabled
   */
  public void iamPrincipalPartitionCheck(String iamPrincipalArn) {
    final Matcher iamRoleArnMatcher =
        DomainConstants.IAM_PRINCIPAL_ARN_PATTERN_ALLOWED.matcher(iamPrincipalArn);

    if (iamRoleArnMatcher.find()) {
      partitionCheck(iamRoleArnMatcher.group("partition"));
    } else {
      final Matcher iamRootArnMatcher =
          DomainConstants.AWS_ACCOUNT_ROOT_ARN_PATTERN.matcher(iamPrincipalArn);
      if (iamRootArnMatcher.find()) {
        partitionCheck(iamRootArnMatcher.group("partition"));
      }
    }
  }

  private String getNamedGroupFromRegexPattern(
      final Pattern pattern, final String groupName, final String input) {
    final Matcher iamRoleArnMatcher = pattern.matcher(input);

    if (!iamRoleArnMatcher.find()) {
      throw ApiException.newBuilder()
          .withApiErrors(new InvalidIamRoleArnApiError(input))
          .withExceptionMessage("ARN does not match pattern: " + pattern.toString())
          .build();
    }
    partitionCheck(iamRoleArnMatcher.group("partition"));

    return iamRoleArnMatcher.group(groupName);
  }

  private void partitionCheck(String partition) {
    if (isAwsGlobalPartition((partition)) && !awsGlobalEnabled) {
      throw ApiException.newBuilder().withApiErrors(DefaultApiError.AWS_GLOBAL_NOT_ALLOWED).build();
    }
    if (isAwsChinaPartition(partition) && !awsChinaEnabled) {
      throw ApiException.newBuilder().withApiErrors(DefaultApiError.AWS_CHINA_NOT_ALLOWED).build();
    }
  }

  private boolean isAwsChinaPartition(String partition) {
    return DomainConstants.AWS_CHINA_PARTITION_NAME.equals(partition);
  }

  private boolean isAwsGlobalPartition(String partition) {
    return DomainConstants.AWS_GLOBAL_PARTITION_NAME.equals(partition);
  }
}
