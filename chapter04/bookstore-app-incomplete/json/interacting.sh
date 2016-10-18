#!/bin/bash
export HOSTNAME=boot2docker
export PORT=8080
export USER_RESOURCE=api/user
export BOOK_RESOURCE=api/book
export ORDER_RESOURCE=api/order
export USER_ENDPOINT=$HOSTNAME:$PORT/$USER_RESOURCE
export BOOK_ENDPOINT=$HOSTNAME:$PORT/$BOOK_RESOURCE
export ORDER_ENDPOINT=$HOSTNAME:$PORT/$ORDER_RESOURCE

function clearJournal {
    docker exec -it cassandra cqlsh -k akka -e "truncate table messages;truncate table config;truncate table metadata;"
}

function clearSnapshot {
    docker exec -it cassandra cqlsh -k akka_snapshot -e "truncate table snapshots;"
}

## User API
function createUser {
    http -v post $USER_ENDPOINT < $1
}

function getUserByEmailAddress {
    http -v get $USER_ENDPOINT/$1
}

function editUserByEmailAddress {
    http -v put $USER_ENDPOINT/$1 < $2
}

function deleteUserByEmailAddress {
    http -v delete $USER_ENDPOINT/$1
}

## Book API
function createBook {
    http -v post $BOOK_ENDPOINT < $1
}

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

echo "clearing journal and snapshots"
clearJournal
clearSnapshot

## Interacting with the user resource
echo "Creating a user"
createUser user.json
echo "Finding a store user by email address"
getUserByEmailAddress chris@masteringakka.com
echo "Editing a store user by email address"
editUserByEmailAddress chris@masteringakka.com user-edit.json
#echo "Deleting a store user by id"
#deleteUserByEmailAddress chris@masteringakka.com

# Interacting with the book resource
echo "Creating a book"
createBook book.json
echo "Getting a book by id"
getBookById 798c8b90-e2b3-4d3e-b01e-cf0896531f0f
echo "Add tag to book by id"
addTagToBookById 798c8b90-e2b3-4d3e-b01e-cf0896531f0f ocean
echo "Remove tag from book by id"
removeTagFromBookById 798c8b90-e2b3-4d3e-b01e-cf0896531f0f ocean
# can't call these until chapter 5
#echo "Finding a book by tag"
#findBookByTag fiction
#echo "Finding a book by two tags"
#findBookByTwoTags fiction scifi
#echo "Finding a book by author"
#findBookByAuthor Verne
echo "Add number of books to inventory by book id"
addNumberOfBooksToInventoryByBookId 798c8b90-e2b3-4d3e-b01e-cf0896531f0f 5
#echo "Deleting a book by id"
#deleteBookById 798c8b90-e2b3-4d3e-b01e-cf0896531f0f

### Interacting with the order resource
echo "Creating an order"
createOrder order.json
echo "Getting an order by id"
getOrderById b2fc7d0a-92ea-483a-bb59-5bc628f68d9b
# can't call these until chapter 5
#echo "Getting orders for a user by user id"
#getOrdersByUserId 1
#echo "Getting orders for a certain book by book id"
#getOrdersByBookId 1
#echo "Getting orders for books with a certain book tag"
#getOrderByBookTag fiction