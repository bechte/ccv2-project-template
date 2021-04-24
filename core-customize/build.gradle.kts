plugins {
    id("sap.commerce.build") version("3.5.0")
    id("sap.commerce.build.ccv2") version("3.5.0")
    id("de.undercouch.download") version("4.1.1")
}
import mpern.sap.commerce.build.tasks.HybrisAntTask
import org.apache.tools.ant.taskdefs.condition.Os

import de.undercouch.gradle.tasks.download.Download
import de.undercouch.gradle.tasks.download.Verify

val DEPENDENCY_FOLDER = "dependencies"
repositories {
    flatDir { dirs(DEPENDENCY_FOLDER) }
    mavenCentral()
}

//Optional: automate downloads from launchpad.support.sap.com
//  remove this block if you use something better, like Maven
//  Recommended reading: 
//  https://github.com/SAP/commerce-gradle-plugin/blob/master/docs/FAQ.md#downloadPlatform
if (project.hasProperty("sUser") && project.hasProperty("sUserPass")) {
    val SUSER = project.property("sUser") as String
    val SUSERPASS = project.property("sUserPass") as String

    val COMMERCE_VERSION = CCV2.manifest.commerceSuiteVersion
    tasks.register<Download>("downloadPlatform") {
        src("https://softwaredownloads.sap.com/file/0020000000576342021")
        dest(file("${DEPENDENCY_FOLDER}/hybris-commerce-suite-${COMMERCE_VERSION}.zip"))
        username(SUSER)
        password(SUSERPASS)
        overwrite(false)
        tempAndMove(true)
        onlyIfModified(true)
        useETag(true)
    }

    tasks.register<Verify>("downloadAndVerifyPlatform") {
        dependsOn("downloadPlatform") 
        src(file("dependencies/hybris-commerce-suite-${COMMERCE_VERSION}.zip"))
        algorithm("SHA-256")
        checksum("f4d1e50a500288c5cc80fc33e60420d363fefcac4443ffa6ea779f51fbc88e0e")
    }

    tasks.named("bootstrapPlatform") {
        dependsOn("downloadAndVerifyPlatform")
    }

    //check if Integration Extension Pack is configured and download it too
    if (CCV2.manifest.extensionPacks.any{"hybris-commerce-integrations".equals(it.name)}) {
        val INTEXTPACK_VERSION = CCV2.manifest.extensionPacks.first{"hybris-commerce-integrations".equals(it.name)}.version
        tasks.register<Download>("downloadIntExtPack") {
            src("https://softwaredownloads.sap.com/file/0020000000567972021")
            dest(file("${DEPENDENCY_FOLDER}/hybris-commerce-integrations-${INTEXTPACK_VERSION}.zip"))
            username(SUSER)
            password(SUSERPASS)
            overwrite(false)
            tempAndMove(true)
            onlyIfModified(true)
            useETag(true)
        }

        tasks.register<Verify>("downloadAndVerifyIntExtPack") {
            dependsOn("downloadIntExtPack")
            src(file("${DEPENDENCY_FOLDER}/hybris-commerce-integrations-${INTEXTPACK_VERSION}.zip"))
            algorithm("SHA-256")
            checksum("0361e959583bcacdfd24de40611fd64d41fa0313090a217b02163e2e8683460a")
        }

        tasks.named("bootstrapPlatform") {
            dependsOn("downloadAndVerifyIntExtPack")
        }
    }
}

tasks.register<WriteProperties>("generateLocalProperties") {
    comment = "GENEREATED AT " + java.time.Instant.now()
    outputFile = project.file("hybris/config/local.properties")

    property("hybris.optional.config.dir", "\${HYBRIS_CONFIG_DIR}/local-config")
}

// https://help.sap.com/viewer/b2f400d4c0414461a4bb7e115dccd779/LATEST/en-US/784f9480cf064d3b81af9cad5739fecc.html
tasks.register<Copy>("enableModeltMock") {
    from("hybris/bin/custom/extras/modelt/extensioninfo.disabled")
    into("hybris/bin/custom/extras/modelt/")
    rename { "extensioninfo.xml" }
}

tasks.named("installManifestAddons") {
    mustRunAfter("generateLocalProperties")
}

tasks.register("setupLocalDevelopment") {
    group = "SAP Commerce"
    description = "Setup local development"
    dependsOn("bootstrapPlatform", "generateLocalProperties", "installManifestAddons", "enableModeltMock")
}
