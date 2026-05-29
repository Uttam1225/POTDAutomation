package com.potd.stepdefinitions;

import com.potd.pages.LoginPage;
import com.potd.utils.ConfigReader;
import com.potd.utils.PageFactory;
import com.potd.utils.PlaywrightManager;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import static org.junit.Assert.assertTrue;

public class LoginSteps {

    private LoginPage loginPage;

    @Given("the user is on the login page")
    public void theUserIsOnTheLoginPage() {
        loginPage = PageFactory.initPage(LoginPage.class, PlaywrightManager.getPage());
        loginPage.navigateToLoginPage();
    }

    /**
     * Reads credentials from config.properties (or env vars GFG_USERNAME / GFG_PASSWORD).
     * No credentials are passed through the feature file.
     */
    @When("the user logs in with credentials from config")
    public void theUserLogsInWithCredentialsFromConfig() {
        String username = ConfigReader.getProperty("username");
        String password = ConfigReader.getProperty("password");

        if (username == null || username.isBlank()) {
            throw new IllegalStateException(
                    "Username not set. Provide 'username' in config.properties or set env var GFG_USERNAME.");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalStateException(
                    "Password not set. Provide 'password' in config.properties or set env var GFG_PASSWORD.");
        }

        loginPage.login(username, password);
    }

    @When("the user enters username {string} and password {string}")
    public void theUserEntersUsernameAndPassword(String username, String password) {
        loginPage.enterUsername(username);
        loginPage.enterPassword(password);
    }

    @And("the user clicks the login button")
    public void theUserClicksTheLoginButton() {
        loginPage.clickLogin();
    }

    @Then("the user should be logged in successfully")
    public void theUserShouldBeLoggedInSuccessfully() {
        assertTrue("Expected successful login — page did not show logged-in state",
                loginPage.isLoginSuccessful());
    }

    @Then("the user should see an error message {string}")
    public void theUserShouldSeeAnErrorMessage(String expectedMessage) {
        assertTrue("Expected error message to be displayed", loginPage.isErrorDisplayed());
        String actualMessage = loginPage.getErrorMessage();
        assertTrue("Error message did not contain: " + expectedMessage,
                actualMessage.contains(expectedMessage));
    }
}
