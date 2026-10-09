//
//  LiveSipTests.swift
//  YeyofoneUITests
//
//  End-to-end checks against a real PBX through the app's own screens. They need an account,
//  supplied by environment variables so no credentials live in the repository:
//
//    TEST_RUNNER_YF_SIP_USER=1005 TEST_RUNNER_YF_SIP_DOMAIN=example.com \
//    TEST_RUNNER_YF_SIP_PASSWORD=... xcodebuild test -scheme YeyofoneUITests ...
//
//  (xcodebuild passes TEST_RUNNER_-prefixed variables to the test runner without the prefix.)
//  Tests skip when the variables are missing. Calls that need a person to answer or ring
//  also need YF_LIVE_PEER, the extension that will take part.
//

import XCTest

final class LiveSipTests: XCTestCase {
    private var app: XCUIApplication!
    private var environment: [String: String] { ProcessInfo.processInfo.environment }

    override func setUpWithError() throws {
        continueAfterFailure = false
        guard environment["YF_SIP_USER"] != nil, environment["YF_SIP_PASSWORD"] != nil, environment["YF_SIP_DOMAIN"] != nil else {
            throw XCTSkip("Set YF_SIP_USER, YF_SIP_PASSWORD and YF_SIP_DOMAIN to run live SIP tests.")
        }
        app = XCUIApplication()
        app.launchArguments += ["-YFResetData", "-YFEngineLog", "-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()
    }

    /// Adds the account, waits for registration, then calls our own extension (the PBX answers it)
    /// and exercises mute, hold and hang-up.
    func testRegistersAndCallsOwnExtension() throws {
        try addAccountAndWaitForRegistration()
        let user = try XCTUnwrap(environment["YF_SIP_USER"])
        dial(user)
        waitForConnectedTimer()

        let mute = button(named: "Mute")
        mute.tap()
        XCTAssertTrue(mute.waitForSelection(true, timeout: 5), "Mute didn't turn on")
        mute.tap()

        app.buttons["Hold"].tap()
        XCTAssertTrue(app.staticTexts["On hold"].waitForExistence(timeout: 10), "Call didn't go on hold")
        app.buttons["Resume"].tap()
        waitForConnectedTimer()

        hangUpAndCheckSummary()
    }

    /// Calls YF_LIVE_PEER; a person answers on that extension, then the test hangs up after 10 seconds.
    func testCallsLivePeer() throws {
        let peer = try XCTUnwrap(environment["YF_LIVE_PEER"], "Set YF_LIVE_PEER to the extension that will answer.")
        try addAccountAndWaitForRegistration()
        dial(peer)
        waitForConnectedTimer(timeout: 60)
        sleep(10)
        hangUpAndCheckSummary()
    }

    /// Waits for YF_LIVE_PEER to call us, answers, talks for 10 seconds and hangs up.
    func testAnswersIncomingCall() throws {
        _ = try XCTUnwrap(environment["YF_LIVE_PEER"], "Set YF_LIVE_PEER to the extension that will call.")
        try addAccountAndWaitForRegistration()
        XCTAssertTrue(app.staticTexts["INCOMING CALL"].waitForExistence(timeout: 120), "No incoming call arrived")
        button(named: "Accept").tap()
        allowMicrophoneIfAsked()
        waitForConnectedTimer()
        sleep(10)
        hangUpAndCheckSummary()
    }

    // MARK: Steps

    private func addAccountAndWaitForRegistration() throws {
        let user = try XCTUnwrap(environment["YF_SIP_USER"])
        app.buttons["Settings"].tap()
        app.staticTexts["Accounts"].tap()
        app.buttons["Add account"].tap()

        type("YeyoFone iOS test", into: app.textFields["Display name"])
        type(user, into: app.textFields["SIP username"])
        type(try XCTUnwrap(environment["YF_SIP_PASSWORD"]), into: app.secureTextFields["Password"])
        type(try XCTUnwrap(environment["YF_SIP_DOMAIN"]), into: app.textFields["Domain"])
        app.buttons["Save"].tap()

        app.staticTexts["YeyoFone iOS test"].tap()
        let registered = app.staticTexts["Registered"].waitForExistence(timeout: 30)
        XCTAssertTrue(registered, "Account didn't register; badge shows: \(badgeText())")
        app.buttons["Back"].tap()
    }

    private func dial(_ number: String) {
        app.buttons["Keypad"].tap()
        for digit in number { app.buttons[String(digit)].tap() }
        app.buttons["Call"].tap()
        allowMicrophoneIfAsked()
    }

    private func waitForConnectedTimer(timeout: TimeInterval = 30) {
        let timer = app.staticTexts.matching(NSPredicate(format: "label MATCHES %@", "^[0-9]{2}:[0-9]{2}$")).firstMatch
        XCTAssertTrue(timer.waitForExistence(timeout: timeout), "Call didn't connect")
    }

    private func hangUpAndCheckSummary() {
        button(named: "Hang up").tap()
        XCTAssertTrue(app.staticTexts["CALL ENDED"].waitForExistence(timeout: 15), "Call-ended screen didn't appear")
        app.buttons["Close"].tap()
    }

    // MARK: Helpers

    private func type(_ text: String, into field: XCUIElement) {
        XCTAssertTrue(field.waitForExistence(timeout: 5), "Missing field \(field)")
        field.tap()
        field.typeText(text)
    }

    /// Call buttons render their caption in capitals, so match the label case-insensitively.
    private func button(named name: String) -> XCUIElement {
        app.buttons.matching(NSPredicate(format: "label ==[c] %@", name)).firstMatch
    }

    private func allowMicrophoneIfAsked() {
        let allow = XCUIApplication(bundleIdentifier: "com.apple.springboard").buttons["Allow"]
        if allow.waitForExistence(timeout: 3) { allow.tap() }
    }

    private func badgeText() -> String {
        ["Registering…", "Registration failed", "Not registered", "Disabled"]
            .first { app.staticTexts[$0].exists } ?? "unknown"
    }
}

private extension XCUIElement {
    func waitForSelection(_ selected: Bool, timeout: TimeInterval) -> Bool {
        let expectation = XCTNSPredicateExpectation(predicate: NSPredicate(format: "isSelected == %@", NSNumber(value: selected)), object: self)
        return XCTWaiter().wait(for: [expectation], timeout: timeout) == .completed
    }
}
