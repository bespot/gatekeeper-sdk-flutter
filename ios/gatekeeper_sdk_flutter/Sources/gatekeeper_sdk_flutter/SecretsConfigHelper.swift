//
//  SecretsConfigHelper.swift
//  gatekeeper_sdk_flutter
//
//  Created by vangelis spyrou on 2/11/26.
//

import Foundation

enum SecretsConfigHelper {
    static var apiBaseUrl: String? { value(for: "API_BASE_URL") }
    static var apiKey: String? { value(for: "API_KEY") }
    static var authTokenUrl: String? { value(for: "AUTH_TOKEN_URL") }
    static var clientId: String? { value(for: "CLIENT_ID") }
    static var clientSecret: String? { value(for: "CLIENT_SECRET") }

    private static func value(for key: String) -> String? {
        guard let value = Bundle.main.object(forInfoDictionaryKey: key) as? String, !value.isEmpty else {
            return nil
        }
        return value
    }
}

