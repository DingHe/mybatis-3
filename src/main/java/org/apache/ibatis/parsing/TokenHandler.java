/*
 *    Copyright 2009-2023 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.parsing;

/**
 * @author Clinton Begin
 */
// TokenHandler 是一个令牌处理器接口。
// 如果把 GenericTokenParser 比作一个“快递员”，负责在长长的文本中寻找被 #{} 或 ${} 包裹的“包裹”；
// 那么 TokenHandler 就是“收件人”，负责打开包裹并决定如何处理里面的东西。
public interface TokenHandler {
  String handleToken(String content);
}
