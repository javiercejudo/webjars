package utils


import akka.util.Timeout
import play.api.i18n.MessagesApi
import play.api.test._

import scala.concurrent.duration._
import scala.util.Try

class LicenseDetectorSpec extends PlaySpecification with GlobalApplication {

  override implicit def defaultAwaitTimeout: Timeout = 30.seconds

  lazy val licenseDetector = application.injector.instanceOf[LicenseDetector]
  lazy val npm = application.injector.instanceOf[NPM]
  lazy val bower = application.injector.instanceOf[Bower]
  lazy val messages = application.injector.instanceOf[MessagesApi]

  def emptyPackageInfo(licenses: Seq[String]) = PackageInfo("", "", "", "", "", "", licenses, Map.empty[String, String], WebJarType.Bower)

  "gitHubLicenseDetect" should {
    "detect the license" in {
      await(licenseDetector.gitHubLicenseDetect(Try("twbs/bootstrap"))) must beEqualTo("MIT")
    }
    "detect another license" in {
      await(licenseDetector.gitHubLicenseDetect(Try("angular/angular"))) must beEqualTo("MIT")
    }
    "detect another license" in {
      await(licenseDetector.gitHubLicenseDetect(Try("T00rk/bootstrap-material-datetimepicker"))) must beEqualTo("MIT")
    }
  }

  "resolveLicenses" should {
    "convert licenses to accepted ones" in {
      val licenses = Seq("BSD 2-Clause", "BSD-2-Clause", "bsd2clause", "GPLv2", "GPLv3", "MIT/X11")
      val result = await(licenseDetector.resolveLicenses(emptyPackageInfo(licenses)))
      result must be equalTo Set("GPL-2.0", "BSD 2-Clause", "GPL-3.0", "MIT")
    }
    "convert SPDX to BinTray" in {
      val licenses = Seq("OFL-1.1")
      val result = await(licenseDetector.resolveLicenses(emptyPackageInfo(licenses)))
      result must be equalTo Set("Openfont-1.1")
    }
    "convert raw license URL to license" in {
      val licenses = Seq("http://polymer.github.io/LICENSE.txt")
      val result = await(licenseDetector.resolveLicenses(emptyPackageInfo(licenses)))
      result must be equalTo Set("BSD 3-Clause")
    }
    "convert github license URL to license" in {
      val licenses = Seq("https://github.com/facebook/flux/blob/master/LICENSE")
      val result = await(licenseDetector.resolveLicenses(emptyPackageInfo(licenses)))
      result must be equalTo Set("BSD 3-Clause")
    }
    "fail to convert incompatible licenses" in {
      await(licenseDetector.resolveLicenses(emptyPackageInfo(Seq("foo")))) must throwA[Exception]
    }
    "fail on license conversion if no valid licenses are found" in {
      await(licenseDetector.resolveLicenses(emptyPackageInfo(Seq()))) must throwA[Exception]
    }
    "succeed with at least one valid license" in {
      val licenses = await(licenseDetector.resolveLicenses(emptyPackageInfo(Seq("foo", "MIT"))))
      licenses must be equalTo Set("MIT")
    }
    "work with SPDX OR expressions" in {
      val licenses = await(licenseDetector.resolveLicenses(emptyPackageInfo(Seq("(Apache-2.0 OR MIT)"))))
      licenses must be equalTo Set("Apache-2.0", "MIT")
    }
    "work with SPDX 'SEE LICENSE IN LICENSE' expressions" in {
      val testPackageInfo = emptyPackageInfo(Seq("SEE LICENSE IN LICENSE")).copy(sourceConnectionUrl = "git://github.com/stacktracejs/error-stack-parser.git")
      val licenses = await(licenseDetector.resolveLicenses(testPackageInfo))
      licenses must be equalTo Set("Unlicense")
    }
    "be able to be fetched from git repos" in {
      val packageInfo = await(npm.info("ms", Some("0.7.1")))
      await(licenseDetector.resolveLicenses(packageInfo)) must beEqualTo(Set("MIT"))
    }
    "be able to be fetched from git repos" in {
      val packageInfo = await(bower.info("git://github.com/mdedetrich/requirejs-plugins", Some("d9c103e7a0")))
      await(licenseDetector.resolveLicenses(packageInfo, Some("d9c103e7a0"))) must beEqualTo(Set("MIT"))
    }
  }

  "chokidar 1.0.1" should {
    "have a license" in {
      val packageInfo = await(npm.info("chokidar", Some("1.0.1")))
      await(licenseDetector.resolveLicenses(packageInfo)) must contain ("MIT")
    }
  }
  "is-dotfile" should {
    "have a license" in {
      val packageInfo = await(npm.info("is-dotfile", Some("1.0.0")))
      await(licenseDetector.resolveLicenses(packageInfo)) must contain ("MIT")
    }
  }

  "jquery info" should {
    "have a license" in {
      val packageInfo = await(bower.info("jquery", Some("1.11.1")))
      await(licenseDetector.resolveLicenses(packageInfo)) must contain("MIT")
    }
  }

  "bootstrap" should {
    "have a license" in {
      val packageInfo = await(bower.info("bootstrap", Some("3.3.2")))
      await(licenseDetector.resolveLicenses(packageInfo)) must contain("MIT")
    }
  }

  "angular" should {
    "have an MIT license" in {
      val packageInfo = await(bower.info("angular", Some("1.4.0")))
      await(licenseDetector.resolveLicenses(packageInfo)) must contain("MIT")
    }
  }
  "angular-equalizer" should {
    "have an MIT license" in {
      val packageInfo = await(bower.info("angular-equalizer", Some("2.0.1")))
      await(licenseDetector.resolveLicenses(packageInfo))must contain("MIT")
    }
  }

  "zeroclipboard 2.2.0" should {
    "have an MIT license" in {
      val packageInfo = await(bower.info("zeroclipboard", Some("2.2.0")))
      await(licenseDetector.resolveLicenses(packageInfo)) must beEqualTo(Set("MIT"))
    }
  }

  "angular-translate 2.7.2" should {
    "fail with a useful error" in {
      val packageInfo = await(bower.info("angular-translate", Some("2.7.2")))
      await(licenseDetector.resolveLicenses(packageInfo)) must throwA[LicenseNotFoundException](messages("licensenotfound", "bower.json", "git://github.com/angular-translate/bower-angular-translate.git", ""))
    }
  }

  "entities 1.0.0" should {
    "fail with a useful error" in {
      val packageInfo = await(npm.info("entities", Some("1.0.0")))
      await(licenseDetector.resolveLicenses(packageInfo)) must beEqualTo(Set("BSD 2-Clause"))
    }
  }

  "New BSD License" should {
    "resolve to BSD 3-Clause" in {
      val licenses = Seq("New BSD License")
      val result = await(licenseDetector.resolveLicenses(emptyPackageInfo(licenses)))
      result must be equalTo Set("BSD 3-Clause")
    }
  }

  /*
  // This is broken due to upstream: https://github.com/webjars/webjars/issues/1265

  "tinymce-dist 4.2.5" should {
    "have an LGPL-2.1 license" in {
      await(bower.info("tinymce-dist", Some("4.2.5"))).licenses must beEqualTo (Set("LGPL-2.1"))
    }
  }
  */

}
