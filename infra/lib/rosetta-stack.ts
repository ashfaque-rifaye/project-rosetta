import * as path from "path";
import { CfnOutput, Duration, RemovalPolicy, Stack, StackProps } from "aws-cdk-lib";
import { Construct } from "constructs";
import * as s3 from "aws-cdk-lib/aws-s3";
import * as s3n from "aws-cdk-lib/aws-s3-notifications";
import * as s3deploy from "aws-cdk-lib/aws-s3-deployment";
import * as dynamodb from "aws-cdk-lib/aws-dynamodb";
import * as lambda from "aws-cdk-lib/aws-lambda";
import * as iam from "aws-cdk-lib/aws-iam";
import * as cloudfront from "aws-cdk-lib/aws-cloudfront";
import * as origins from "aws-cdk-lib/aws-cloudfront-origins";
import * as apigw from "aws-cdk-lib/aws-apigatewayv2";
import { HttpLambdaIntegration } from "aws-cdk-lib/aws-apigatewayv2-integrations";

/**
 * Project Rosetta backend.
 *
 *   kid phone ──PUT photo──► S3 uploads/ (deleted after 1 day) ──event──► Agent Lambda (Strands Quest Master
 *                                                                          + Scout/Coach/Judge/Keeper on Bedrock)
 *   TV / phones ──HTTP API──► API Lambda ──► DynamoDB (household doc) ◄──────────────┘
 *   phone pages ◄── CloudFront ◄── S3 web/
 */
export class RosettaStack extends Stack {
  constructor(scope: Construct, id: string, props?: StackProps) {
    super(scope, id, props);
    const modelId = this.node.tryGetContext("modelId") ?? "us.amazon.nova-pro-v1:0";

    const uploads = new s3.Bucket(this, "Uploads", {
      blockPublicAccess: s3.BlockPublicAccess.BLOCK_ALL,
      encryption: s3.BucketEncryption.S3_MANAGED,
      enforceSSL: true,
      removalPolicy: RemovalPolicy.DESTROY,
      autoDeleteObjects: true,
      // Kids' room photos are working data, not an archive.
      lifecycleRules: [{ prefix: "uploads/", expiration: Duration.days(1) }],
      cors: [{ allowedMethods: [s3.HttpMethods.PUT, s3.HttpMethods.GET], allowedOrigins: ["*"], allowedHeaders: ["*"] }],
    });

    const table = new dynamodb.Table(this, "Households", {
      partitionKey: { name: "code", type: dynamodb.AttributeType.STRING },
      billingMode: dynamodb.BillingMode.PAY_PER_REQUEST,
      removalPolicy: RemovalPolicy.DESTROY,
    });

    const code = lambda.Code.fromAsset(path.join(__dirname, "..", "..", "agents", "dist"));
    const env = { ROSETTA_BUCKET: uploads.bucketName, ROSETTA_TABLE: table.tableName, ROSETTA_MODEL_ID: modelId };

    const apiFn = new lambda.Function(this, "ApiFn", {
      runtime: lambda.Runtime.PYTHON_3_12, handler: "rosetta_agents.handlers.api", code, environment: env,
      memorySize: 512, timeout: Duration.seconds(15),
    });
    const agentFn = new lambda.Function(this, "AgentCrewFn", {
      runtime: lambda.Runtime.PYTHON_3_12, handler: "rosetta_agents.handlers.on_upload", code, environment: env,
      memorySize: 1024, timeout: Duration.seconds(120),
    });

    for (const fn of [apiFn, agentFn]) {
      table.grantReadWriteData(fn);
      uploads.grantReadWrite(fn);
    }
    agentFn.addToRolePolicy(new iam.PolicyStatement({
      actions: ["bedrock:InvokeModel", "bedrock:InvokeModelWithResponseStream", "bedrock:Converse", "bedrock:ConverseStream"],
      resources: ["*"],
    }));
    uploads.addEventNotification(s3.EventType.OBJECT_CREATED, new s3n.LambdaDestination(agentFn), { prefix: "uploads/" });

    const api = new apigw.HttpApi(this, "Api", {
      corsPreflight: { allowOrigins: ["*"], allowMethods: [apigw.CorsHttpMethod.ANY], allowHeaders: ["content-type"] },
    });
    api.addRoutes({ path: "/{proxy+}", methods: [apigw.HttpMethod.ANY], integration: new HttpLambdaIntegration("ApiInt", apiFn) });

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
    new s3deploy.BucketDeployment(this, "WebDeploy", {
      destinationBucket: site,
      distribution: cdn,
      sources: [
        s3deploy.Source.asset(path.join(__dirname, "..", "..", "web"), { exclude: ["config.js"] }),
        s3deploy.Source.data("config.js", `window.ROSETTA_CONFIG = { apiUrl: "${api.apiEndpoint}" };\n`),
      ],
    });

    new CfnOutput(this, "ApiUrl", { value: api.apiEndpoint });
    new CfnOutput(this, "WebUrl", { value: `https://${cdn.distributionDomainName}` });
    new CfnOutput(this, "SeedCommand", { value: `curl -X POST ${api.apiEndpoint}/household/R4KX/reset` });
  }
}
