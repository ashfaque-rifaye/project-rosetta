# Model providers

Rosetta's agent crew is model-agnostic. Every prompt and JSON schema is written once as an **Amazon Bedrock Converse** tool spec, and every model call goes through one adapter (`agents/rosetta_agents/vision.py → _converse`). The provider is chosen at deploy time with one CDK context flag; storage, the API, events, alerts and the agent orchestration stay on AWS either way.

| Flag | Model calls go to | Notes |
|---|---|---|
| `npx cdk deploy -c aiProvider=bedrock` | **Amazon Bedrock**, Amazon Nova Pro (`ROSETTA_MODEL_ID`, default `us.amazon.nova-pro-v1:0`) | AWS only. Quest Master runs on the Strands `BedrockModel`. The Keeper's lines show as on-screen text. |
| `npx cdk deploy` (default today) | An external multimodal provider via `agents/rosetta_agents/gemini.py` | Adds speech: voice-note transcription and the Keeper's spoken voice. The hosted demo uses this during the hackathon to stay within credits. |

## External provider setup (keyless)

The Lambdas reach the external provider with **Workload Identity Federation**: Google Cloud trusts this stack's AWS IAM roles, so no API key exists anywhere. Once, after the first deploy (so the roles exist):

```bash
gcloud iam service-accounts create rosetta-agents
gcloud projects add-iam-policy-binding <project> --member serviceAccount:rosetta-agents@<project>.iam.gserviceaccount.com --role roles/aiplatform.user
gcloud iam workload-identity-pools create rosetta-aws --location global
gcloud iam workload-identity-pools providers create-aws aws-rosetta --location global --workload-identity-pool rosetta-aws --account-id <aws-account> --attribute-mapping="google.subject=assertion.userid,attribute.aws_role=assertion.arn.contains('assumed-role') ? assertion.arn.extract('{account_arn}assumed-role/') + 'assumed-role/' + assertion.arn.extract('assumed-role/{role_name}/') : assertion.arn"
# for each of the AgentCrewFn, ApiFn and ScheduledFn roles:
gcloud iam service-accounts add-iam-policy-binding rosetta-agents@<project>.iam.gserviceaccount.com --role roles/iam.workloadIdentityUser --member "principalSet://iam.googleapis.com/projects/<number>/locations/global/workloadIdentityPools/rosetta-aws/attribute.aws_role/arn:aws:sts::<aws-account>:assumed-role/<role-name>"
gcloud iam workload-identity-pools create-cred-config projects/<number>/locations/global/workloadIdentityPools/rosetta-aws/providers/aws-rosetta --service-account rosetta-agents@<project>.iam.gserviceaccount.com --aws --output-file agents/gcp_wif.json
```

Then rebuild the bundle (`python agents/build_lambda.py`) and deploy again. `python agents/warm_voice.py` pre-voices the repeating Keeper lines and caches them in Amazon S3.
