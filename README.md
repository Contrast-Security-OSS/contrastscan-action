# Use Contrast Scan to analyze your code

This github action will enable you to use Contrast Scan to detect vulnerabilities in your code.

This action can currently scan JVM bytecode artifacts produced from Java source code.

## Requirements

You will need the following items to use Contrast Scan:

* A valid account with Contrast's Security Platform.
  * service account
  * service key
  * api key
  * organizationId
  
## Usage

All Contrast-related account secrets should be configured as github secrets and will be passed to the scanner via
environment variables in the github runner.

A simple workflow to get going is:

```yaml
on:
  # Trigger analysis when pushing to main or an existing pull requests.  Also trigger on 
  # new pull requests
  push:
    branches:
      - master
  pull_request:
      types: [opened, synchronize, reopened]
name: Common Workflow
jobs:
  build_and_scan:
    runs-on: ubuntu-latest
    # check out project
    steps:
    - uses: actions/checkout@v2
        
    # steps to build the artifact you want to scan
    # -name: Build Project
    # ...
      
    # Scan Artifact    
    - name: Contrast Scan
      uses: Contrast-Security-OSS/contrastscan-action@v1
      with:
        artifact: mypath/target/myartifact.jar
        
      env:
        GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
        CONTRAST__API__USER_NAME: "<user name>"
        CONTRAST__API__ORGANIZATION_ID: "<your organization ID here>"
        CONTRAST__API__SERVICE_KEY: ${{ secrets.CONTRAST_SERVICE_KEY }}
        CONTRAST__API__API_KEY: ${{ secrets.CONTRAST_API_KEY }}
```

### Secrets
**TBD: how does a user obtain these Contrast Platform secrets if needed? **

- `CONTRAST__API__SERVICE_KEY` – **Required** this is the token used to authenticate access to the Contrast Security 
  Platform.
- `CONTRAST__API__API_KEY` – **Required** this is the token used to authenticate access to the Contrast Security
  Platform. 
- *`GITHUB_TOKEN` – Provided by Github (see [Authenticating with the GITHUB_TOKEN](https://help.github.com/en/actions/automating-your-workflow-with-github-actions/authenticating-with-the-github_token)).*

## Example of analysis from a pull request

**TBD: insert picture of PR with annotations and labeling here** 

## Do not use this GitHub action if you are in the following situations

* Your code is built with Maven: run 'org.contrastsecurity.maven:scan' during the build (see [Contrast Maven Plugin](https://github.com/Contrast-Security-OSS/contrast-maven-plugin))

## Initial On-boarding of using the action.

These instructions assume you already have setup a github workflow to build your project.  If not, see this first... **TBD**

1. Create a branch of your code to add the scanning action to your workflow. This is typically located at
   `./github/workflows/build.yml`
2. Add the `contrastscan-action` as described above to your workflow and commit.
3. After committing, create a Pull Request to merge the update back to your main branch.  If using the parameters in
   the example workflow above, creating the PR will trigger the Scan to run.  You will see the extra "Code Scanning"
   check appear in the PR.
4. Based on Contrast Scan analysis findings, GitHub will control whether a build check will fail or not.  It does
   this by comparing the code scanning analysis of the PR to the last code scanning analysis of the destination branch.
   GitHub will fail the check if the code scanning analysis has additional findings that the destination branch does not
   have. This is intended to prevent new code from introducing vulnerabilities.
5. On the first run of Contrast Scan, the destination branch will have not had any code scanning analysis performed and
   thus all vulnerabilities will be discovered as "new", but since there is nothing to compare it to on the destination
   branch GitHub will not fail the code scanning check.  Since its likely there will be new findings with the addition 
   of a new security scanning tool, Contrast Scan in this case, we don't want to fail and block merging the PR that
   adds the scanning tool, forcing the owner of the PR to now fix all the newly exposed vulnerabilities that already
   existed in the code base.
6. Once the PR is merged back to the main branch, the contrastscan-action will run on the main branch and a code
   scanning analysis will be added to GitHub for it.  This will cause the "Security" Tab of the repo to now show all
   the current vulnerabilities the code base has on its main branch. After this occurs, now all new PRs that are created
   where the contrastscan-action is run will fail the code scanning check if they introduce new vulnerabilities beyond
   the baseline we just established.

## Have question or feedback?

**TBD: provide contact info...  route through a developer oriented resource area. or perhaps directly with in github on
this project**



