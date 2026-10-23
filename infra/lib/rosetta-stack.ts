import * as fs from "fs";
import * as path from "path";
import { CfnOutput, Duration, RemovalPolicy, Size, Stack, StackProps } from "aws-cdk-lib";
import { Construct } from "constructs";
import * as s3 from "aws-cdk-lib/aws-s3";
import * as s3n from "aws-cdk-lib/aws-s3-notifications";
import * as s3deploy from "aws-cdk-lib/aws-s3-deployment";
import * as dynamodb from "aws-cdk-lib/aws-dynamodb";
import * as lambda from "aws-cdk-lib/aws-lambda";
import * as iam from "aws-cdk-lib/aws-iam";
import * as events from "aws-cdk-lib/aws-events";
import * as targets from "aws-cdk-lib/aws-events-targets";
import * as cloudfront from "aws-cdk-lib/aws-cloudfront";
import * as origins from "aws-cdk-lib/aws-cloudfront-origins";
import * as apigw from "aws-cdk-lib/aws-apigatewayv2";
import { HttpLambdaIntegration } from "aws-cdk-lib/aws-apigatewayv2-integrations";

/**
 * Project Rosetta backend.
 *
 *   kid phone ──PUT photo──► S3 uploads/ (deleted after 1 day) ──event──► Agent Lambda (Strands Quest Master
 *                                                                          + Scout/Coach/Judge/Keeper on Bedrock,
 *                                                                          voice on Polly, voice notes on Transcribe)
 *   TV / phones ──HTTP API──► API Lambda ──► DynamoDB (household doc) ◄──────────────┘
 *   phone pages ◄── CloudFront ◄── S3 web/
 *   EventBridge ──► Scheduled Lambda: evening streak nudge, weekly Growth digest (Digest agent)
 *   parent alerts: Web Push (VAPID) · SES email · SNS SMS (each optional, per parent)
 */
export class RosettaStack extends Stack {
  constructor(scope: Construct, id: string, props?: StackProps) {
    super(scope, id, props);
    const modelId = this.node.tryGetContext("modelId") ?? "us.amazon.nova-2-lite-v1:0";

    const uploads = new s3.Bucket(this, "Uploads", {
      blockPublicAccess: s3.BlockPublicAccess.BLOCK_ALL,
      encryption: s3.BucketEncryption.S3_MANAGED,
      enforceSSL: true,
      removalPolicy: RemovalPolicy.DESTROY,
      autoDeleteObjects: true,
      // Kids' room photos are working data, not an archive.
      lifecycleRules: [{ prefix: "uploads/", expiration: Duration.days(1) }, { prefix: "voice/", expiration: Duration.days(1) },
        { prefix: "transcripts/", expiration: Duration.days(1) },
        // Kids' notes, voice notes and proof photos that parents can look back on.
        { prefix: "memories/", expiration: Duration.days(90) }],
      cors: [{ allowedMethods: [s3.HttpMethods.PUT, s3.HttpMethods.GET], allowedOrigins: ["*"], allowedHeaders: ["*"] }],
    });

    const table = new dynamodb.Table(this, "Households", {
      partitionKey: { name: "code", type: dynamodb.AttributeType.STRING },
      billingMode: dynamodb.BillingMode.PAY_PER_REQUEST,
      removalPolicy: RemovalPolicy.DESTROY,
    });

    const code = lambda.Code.fromAsset(path.join(__dirname, "..", "..", "agents", "dist"));
    // Web-push keys live in infra/.vapid.json (git-ignored); SES sender is optional context (-c sesFrom=...).
    const vapidFile = path.join(__dirname, "..", ".vapid.json");
    const vapid = fs.existsSync(vapidFile) ? JSON.parse(fs.readFileSync(vapidFile, "utf8")) : { public: "", private: "" };
    const site = new s3.Bucket(this, "Web", {
      blockPublicAccess: s3.BlockPublicAccess.BLOCK_ALL, removalPolicy: RemovalPolicy.DESTROY, autoDeleteObjects: true,
    });
    const cdn = new cloudfront.Distribution(this, "WebCdn", {
      defaultBehavior: {
        origin: origins.S3BucketOrigin.withOriginAccessControl(site),
        viewerProtocolPolicy: cloudfront.ViewerProtocolPolicy.REDIRECT_TO_HTTPS,
      },
      defaultRootObject: "kid.html",
    });
    const env = {
      ROSETTA_BUCKET: uploads.bucketName, ROSETTA_TABLE: table.tableName, ROSETTA_MODEL_ID: modelId,
      ROSETTA_WEB_URL: `https://${cdn.distributionDomainName}`,
      ROSETTA_VAPID_PUBLIC: vapid.public, ROSETTA_VAPID_PRIVATE: vapid.private,
      ROSETTA_SES_FROM: this.node.tryGetContext("sesFrom") ?? "",
    };

    const apiFn = new lambda.Function(this, "ApiFn", {
      runtime: lambda.Runtime.PYTHON_3_12, handler: "rosetta_agents.handlers.api", code, environment: env,
      memorySize: 512, timeout: Duration.seconds(30),  // quiz/start asks the Quizmaster agent
    });
    const agentFn = new lambda.Function(this, "AgentCrewFn", {
      runtime: lambda.Runtime.PYTHON_3_12, handler: "rosetta_agents.handlers.on_upload", code, environment: env,
      // A voice note waits for Amazon Transcribe (usually ~10 s, now and then over a minute). One run per upload:
      // a retried run would replay a finished photo over newer state.
      memorySize: 1024, timeout: Duration.seconds(240), retryAttempts: 0,
    });

    const scheduledFn = new lambda.Function(this, "ScheduledFn", {
      runtime: lambda.Runtime.PYTHON_3_12, handler: "rosetta_agents.handlers.scheduled", code, environment: env,
      memorySize: 512, timeout: Duration.seconds(60),
    });

    for (const fn of [apiFn, agentFn, scheduledFn]) {
      table.grantReadWriteData(fn);
      uploads.grantReadWrite(fn);
      fn.addToRolePolicy(new iam.PolicyStatement({
        actions: ["bedrock:InvokeModel", "bedrock:InvokeModelWithResponseStream", "bedrock:Converse", "bedrock:ConverseStream"],
        resources: ["*"],
      }));
      // The Keeper's voice (Amazon Polly) and kids' voice notes (Amazon Transcribe reads them from the uploads bucket).
      fn.addToRolePolicy(new iam.PolicyStatement({
        actions: ["polly:SynthesizeSpeech", "transcribe:StartTranscriptionJob", "transcribe:GetTranscriptionJob"],
        resources: ["*"],
      }));
      // Parent alerts: email (only from a verified SES sender) and SMS for urgent alerts.
      fn.addToRolePolicy(new iam.PolicyStatement({ actions: ["ses:SendEmail", "sns:Publish"], resources: ["*"] }));
    }
    // 18:30 IST every evening: streak-at-risk nudges. Sunday 18:00 IST: the weekly Growth digest.
    new events.Rule(this, "StreakNudge", { schedule: events.Schedule.cron({ minute: "0", hour: "13" }) })
      .addTarget(new targets.LambdaFunction(scheduledFn, { event: events.RuleTargetInput.fromObject({ job: "streak" }) }));
    new events.Rule(this, "WeeklyDigest", { schedule: events.Schedule.cron({ minute: "30", hour: "12", weekDay: "SUN" }) })
      .addTarget(new targets.LambdaFunction(scheduledFn, { event: events.RuleTargetInput.fromObject({ job: "digest" }) }));
    uploads.addEventNotification(s3.EventType.OBJECT_CREATED, new s3n.LambdaDestination(agentFn), { prefix: "uploads/" });

    const api = new apigw.HttpApi(this, "Api", {
      corsPreflight: { allowOrigins: ["*"], allowMethods: [apigw.CorsHttpMethod.ANY], allowHeaders: ["content-type", "x-parent-pin"] },
    });
    api.addRoutes({ path: "/{proxy+}", methods: [apigw.HttpMethod.ANY], integration: new HttpLambdaIntegration("ApiInt", apiFn) });

    new s3deploy.BucketDeployment(this, "WebDeploy", {
      destinationBucket: site,
      distribution: cdn,
      sources: [
        s3deploy.Source.asset(path.join(__dirname, "..", "..", "web"), { exclude: ["config.js"] }),
        s3deploy.Source.data("config.js", `window.ROSETTA_CONFIG = { apiUrl: "${api.apiEndpoint}" };\n`),
      ],
      exclude: ["downloads/*"],   // the APKs below are deployed separately; don't prune them
    });

    // The TV install page (web/tv.html) links to the TV app and Quiz Night APKs: put them in dist/downloads/ to publish.
    const downloads = path.join(__dirname, "..", "..", "dist", "downloads");
    if (fs.existsSync(downloads)) {
      new s3deploy.BucketDeployment(this, "DownloadsDeploy", {
        destinationBucket: site,
        destinationKeyPrefix: "downloads/",
        distribution: cdn,
        distributionPaths: ["/downloads/*"],
        sources: [s3deploy.Source.asset(downloads)],
        contentType: "application/vnd.android.package-archive",
        memoryLimit: 2048,
        ephemeralStorageSize: Size.gibibytes(2),
      });
    }

    new CfnOutput(this, "ApiUrl", { value: api.apiEndpoint });
    new CfnOutput(this, "WebUrl", { value: `https://${cdn.distributionDomainName}` });
    new CfnOutput(this, "SeedCommand", { value: `curl -X POST ${api.apiEndpoint}/household/R4KX/reset` });
  }
}
