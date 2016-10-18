#!/bin/bash
export HOSTNAME=boot2docker
export PORT=8080
export USER_RESOURCE=api/user
export BOOK_RESOURCE=api/book
export ORDER_RESOURCE=api/order
export USER_ENDPOINT=$HOSTNAME:$PORT/$USER_RESOURCE
export BOOK_ENDPOINT=$HOSTNAME:$PORT/$BOOK_RESOURCE
export ORDER_ENDPOINT=$HOSTNAME:$PORT/$ORDER_RESOURCE

function clearTable {
    docker exec -it postgres psql --dbname=akkaexampleapp --username=docker -c "truncate $1 cascade"
    docker exec -it postgres psql --dbname=akkaexampleapp --username=docker -c "ALTER SEQUENCE $1_id_seq RESTART WITH 1"
}

function clearTables {
 clearTable booktag
 clearTable book
 clearTable storeuser
 clearTable creditcardtransaction
 clearTable salesorderheader
 clearTable salesorderlineitem
}

## User API
function createUser {
    http -v post $USER_ENDPOINT < $1
}

function getUserById {
    http -v get $USER_ENDPOINT/$1
}

function getUserByEmailAddress {
    http -v get $USER_ENDPOINT email==$1
}

function editUserById {
    http -v put $USER_ENDPOINT/$1 < $2
}

function deleteUserById {
    http -v delete $USER_ENDPOINT/$1
}

function createBook {
    http -v post $BOOK_ENDPOINT < $1
}

## Book API
function getBookById {
    http -v get $BOOK_ENDPOINT/$1
}

function addTagToBookById {
    http -v put $BOOK_ENDPOINT/$1/tag/$2
}

function removeTagFromBookById {
    http -v delete $BOOK_ENDPOINT/$1/tag/$2
}

function findBookByTag {
    http -v get $BOOK_ENDPOINT tag==$1
}

function findBookByTwoTags {
    http -v get $BOOK_ENDPOINT tag==$1 tag==$2
}

function findBookByAuthor {
    http -v get $BOOK_ENDPOINT author==$1
}

function addNumberOfBooksToInventoryByBookId {
    http -v put $BOOK_ENDPOINT/$1/inventory/$2
}

function deleteBookById {
    http -v delete $BOOK_ENDPOINT/$1
}

## Order API
function createOrder {
    http -v post $ORDER_ENDPOINT < $1
}

function getOrderById {
    http -v get $ORDER_ENDPOINT/$1
}

function getOrdersByUserId {
    http -v get $ORDER_ENDPOINT userId==$1
}

function getOrdersByBookId {
    http -v get $ORDER_ENDPOINT bookId==$1
}

function getOrderByBookTag {
    http -v get $ORDER_ENDPOINT bookTag==$1
}

echo "clearing database tables"
clearTables

## Interacting with the user resource
echo "Creating a user"
createUser user.json
echo "Getting a store user by id"
getUserById 1
echo "Finding a store user by email address"
getUserByEmailAddress chris@masteringakka.com
echo "Editing a store user by id"
editUserById 1 user-edit.json
#echo "Deleting a store user by id"
#deleteUserById 1

## Interacting with the book resource
echo "Creating a book"
createBook book.json
echo "Getting a book by id"
getBookById 1
echo "Add tag to book by id"
addTagToBookById 1 ocean
echo "Remove tag from book by id"
removeTagFromBookById 1 ocean
echo "Finding a book by tag"
findBookByTag fiction
echo "Finding a book by two tags"
findBookByTwoTags fiction scifi
echo "Finding a book by author"
findBookByAuthor Verne
echo "Add number of books to inventory by book id"
addNumberOfBooksToInventoryByBookId 1 5
#echo "Deleting a book by id"
#deleteBookById 1

## Interacting with the order resource
echo "Creating an order"
createOrder order.json
echo "Getting an order by id"
getOrderById 1
echo "Getting orders for a user by user id"
getOrdersByUserId 1
echo "Getting orders for a certain book by book id"
getOrdersByBookId 1
echo "Getting orders for books with a certain book tag"
getOrderByBookTag fiction