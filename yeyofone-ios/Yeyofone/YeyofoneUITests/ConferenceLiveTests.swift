//
//  ConferenceLiveTests.swift
//  YeyofoneUITests
//
//  Runs the iPhone as the conference organiser against the live PBX, using the account and
//  contacts already installed in the app (it does not reset any data). The two people it calls
//  must answer (or be set to auto-answer):
//
//    TEST_RUNNER_YF_PEER_ONE=1005 TEST_RUNNER_YF_PEER_TWO=1001 TEST_RUNNER_YF_PEER_TWO_NAME="Pascal Kankonde" \
//    xcodebuild test -scheme Yeyofone -only-testing:YeyofoneUITests/ConferenceLiveTests ...
//
//  The second person is picked from the contact list, so they must be a saved contact.
//

import XCTest

final class ConferenceLiveTests: XCTestCase {
    private var app: XCUIApplication!
    private var environment: [String: String] { ProcessInfo.processInfo.environment }

    override func setUpWithError() throws {
        continueAfterFailure = false
        guard environment["YF_PEER_ONE"] != nil, environment["YF_PEER_TWO_NAME"] != nil else {
            throw XCTSkip("Set YF_PEER_ONE and YF_PEER_TWO_NAME to run the live conference test.")
        }
        app = XCUIApplication()
        app.launchArguments += ["-YFEngineLog", "-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()
    }

    func testOrganisesThreeWayConference() throws {
        let one = try XCTUnwrap(environment["YF_PEER_ONE"])
        let twoName = try XCTUnwrap(environment["YF_PEER_TWO_NAME"])

        snap("1-home")
        app.buttons["Keypad"].tap()
        for digit in one { app.buttons[String(digit)].tap() }
        app.buttons["Call"].tap()
        allowMicrophoneIfAsked()
        snap("2-calling-first")
        let timer = app.staticTexts.matching(NSPredicate(format: "label MATCHES %@", "^[0-9]{2}:[0-9]{2}$")).firstMatch
        XCTAssertTrue(timer.waitForExistence(timeout: 60), "First person didn't answer")
        snap("3-first-connected")

        button(named: "More").tap()
        snap("4-more-options")
        app.buttons.containing(.staticText, identifier: "Add call").firstMatch.tap()
        snap("5-picker")
        app.buttons.containing(.staticText, identifier: twoName).firstMatch.tap()

        XCTAssertTrue(app.staticTexts["Conference call"].waitForExistence(timeout: 10), "Conference screen didn't appear")
        snap("6-adding-second")
        sleep(3)
        snap("7-second-ringing")

        let active = app.staticTexts.matching(identifier: "Active")
        let bothActive = NSPredicate(format: "count == 2")
        let joined = XCTNSPredicateExpectation(predicate: bothActive, object: active)
        let result = XCTWaiter().wait(for: [joined], timeout: 60)
        snap("8-after-answer")
        XCTAssertEqual(result, .completed, "Second person never showed as Active")

        sleep(15)
        snap("9-in-conference")
        button(named: "Hang up").tap()
        snap("10-ended")
    }

    /// Waits for someone else to add this phone to a conference, accepts, stays for 25 seconds, then hangs up.
    func testJoinsConferenceAsThirdPerson() throws {
        snap("1-waiting")
        XCTAssertTrue(app.staticTexts["INCOMING CALL"].waitForExistence(timeout: 180), "No incoming call arrived")
        snap("2-incoming")
        button(named: "Accept").tap()
        allowMicrophoneIfAsked()
        // When the organiser shares the conference this phone shows the conference screen instead of a call timer.
        let timer = app.staticTexts.matching(NSPredicate(format: "label MATCHES %@", "^[0-9]{2}:[0-9]{2}$")).firstMatch
        let conference = app.staticTexts["Conference call"]
        XCTAssertTrue(timer.waitForExistence(timeout: 30) || conference.waitForExistence(timeout: 30), "Call didn't connect")
        snap("3-connected")
        XCTAssertTrue(conference.waitForExistence(timeout: 60), "Organiser's conference screen never appeared")
        sleep(10)
        snap("4-in-conference")
        button(named: "Hang up").tap()
        snap("5-ended")
    }

    // MARK: Helpers

    private func snap(_ name: String) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    private func button(named name: String) -> XCUIElement {
        app.buttons.matching(NSPredicate(format: "label ==[c] %@", name)).firstMatch
    }

    private func allowMicrophoneIfAsked() {
        let allow = XCUIApplication(bundleIdentifier: "com.apple.springboard").buttons["Allow"]
        if allow.waitForExistence(timeout: 3) { allow.tap() }
    }
}
