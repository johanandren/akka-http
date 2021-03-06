# formField

## Description

Allows extracting a single Form field sent in the request. Data posted from [HTML Forms](https://www.w3.org/TR/html401/interact/forms.html#h-17.13.4) is either of type `application/x-www-form-urlencoded` or of type `multipart/form-data`.

@scala[See @ref[formFields](formFields.md) for an in-depth description.]

## Example

Scala
:  @@snip [FormFieldDirectivesExamplesSpec.scala](/docs/src/test/scala/docs/http/scaladsl/server/directives/FormFieldDirectivesExamplesSpec.scala) { #formField }

Java
:  @@snip [FormFieldDirectivesExamplesTest.java](/docs/src/test/java/docs/http/javadsl/server/directives/FormFieldDirectivesExamplesTest.java) { #formField }
